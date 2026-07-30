package com.example.forwarder

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object ForwarderEngine {
    private const val TAG = "ForwarderEngine"
    private const val PREFS_NAME = "ForwarderPrefs"
    private val executor = Executors.newFixedThreadPool(2)

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo != null && networkInfo.isConnected
        }
    }

    fun forwardMessage(context: Context, source: String, sender: String, content: String) {
        executor.execute {
            val prefs = getPrefs(context)
            val filterMode = prefs.getString("filter_mode", "ALL") ?: "ALL"
            val isSms = source.contains("SMS", ignoreCase = true)

            if (filterMode == "SMS_ONLY" && !isSms) {
                Log.d(TAG, "Filter mode [$filterMode]: Ignored non-SMS message from $source")
                return@execute
            }
            if (filterMode == "NOTIFICATION_ONLY" && isSms) {
                Log.d(TAG, "Filter mode [$filterMode]: Ignored SIM SMS message")
                return@execute
            }

            if (!isNetworkAvailable(context)) {
                Log.d(TAG, "Offline mode: Queuing message into SQLite database")
                val dbHelper = DatabaseHelper(context)
                dbHelper.insertPendingMessage(source, sender, content)
                return@execute
            }

            val success = processSend(context, source, sender, content)
            if (!success) {
                Log.d(TAG, "Transmission failed: Saving to SQLite offline queue")
                val dbHelper = DatabaseHelper(context)
                dbHelper.insertPendingMessage(source, sender, content)
            }
        }
    }

    fun flushPendingMessages(context: Context) {
        executor.execute {
            if (!isNetworkAvailable(context)) return@execute

            val dbHelper = DatabaseHelper(context)
            val pendingList = dbHelper.getAllPendingMessages()

            if (pendingList.isEmpty()) return@execute

            Log.d(TAG, "Flushing ${pendingList.size} pending offline messages...")
            for (msg in pendingList) {
                val sent = processSend(context, msg.source, msg.sender, msg.content)
                if (sent) {
                    dbHelper.deletePendingMessage(msg.id)
                    Log.d(TAG, "Pending message ${msg.id} successfully sent and cleared from SQLite.")
                } else {
                    Log.d(TAG, "Failed to send pending message ${msg.id}. Stopping flush.")
                    break
                }
            }
        }
    }

    private fun processSend(context: Context, source: String, sender: String, content: String): Boolean {
        val prefs = getPrefs(context)
        val botToken = prefs.getString("tg_bot_token", "") ?: ""
        val chatId = prefs.getString("tg_chat_id", "") ?: ""
        val supabaseUrl = prefs.getString("supabase_url", "") ?: ""
        val supabaseKey = prefs.getString("supabase_key", "") ?: ""
        val emailWebhook = prefs.getString("email_webhook", "") ?: ""

        val enableTg = prefs.getBoolean("enable_telegram", true)
        val enableSb = prefs.getBoolean("enable_supabase", true)
        val enableWh = prefs.getBoolean("enable_webhook", true)

        val formattedMessage = "📱 *[$source]*\n👤 *From:* $sender\n💬 *Message:* $content"

        var telegramSuccess = true
        var supabaseSuccess = true
        var webhookSuccess = true

        // 1. Send to Telegram
        if (enableTg && botToken.isNotBlank() && chatId.isNotBlank()) {
            telegramSuccess = sendTelegram(botToken, chatId, formattedMessage)
        }

        // 2. Send to Supabase
        if (enableSb && supabaseUrl.isNotBlank() && supabaseKey.isNotBlank()) {
            supabaseSuccess = sendSupabase(supabaseUrl, supabaseKey, source, sender, content)
        }

        // 3. Send to Webhook
        if (enableWh && emailWebhook.isNotBlank()) {
            webhookSuccess = sendWebhook(emailWebhook, source, sender, content)
        }

        return telegramSuccess && supabaseSuccess && webhookSuccess
    }

    private fun sendTelegram(botToken: String, chatId: String, text: String): Boolean {
        return try {
            val urlString = "https://api.telegram.org/bot$botToken/sendMessage"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            val jsonParam = JSONObject()
            jsonParam.put("chat_id", chatId)
            jsonParam.put("text", text)
            jsonParam.put("parse_mode", "Markdown")

            val os: OutputStream = conn.outputStream
            os.write(jsonParam.toString().toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()

            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Error sending to Telegram", e)
            false
        }
    }

    private fun sendSupabase(baseUrl: String, apiKey: String, source: String, sender: String, content: String): Boolean {
        return try {
            val targetUrl = if (baseUrl.endsWith("/")) "${baseUrl}rest/v1/notifications" else "$baseUrl/rest/v1/notifications"
            val url = URL(targetUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", apiKey)
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Prefer", "return=minimal")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            val jsonParam = JSONObject()
            jsonParam.put("source", source)
            jsonParam.put("sender", sender)
            jsonParam.put("content", content)
            jsonParam.put("created_at", System.currentTimeMillis())

            val os: OutputStream = conn.outputStream
            os.write(jsonParam.toString().toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()

            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Error sending to Supabase", e)
            false
        }
    }

    private fun sendWebhook(webhookUrl: String, source: String, sender: String, content: String): Boolean {
        return try {
            val url = URL(webhookUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            val jsonParam = JSONObject()
            jsonParam.put("source", source)
            jsonParam.put("sender", sender)
            jsonParam.put("content", content)
            jsonParam.put("timestamp", System.currentTimeMillis())

            val os: OutputStream = conn.outputStream
            os.write(jsonParam.toString().toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()

            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Error sending to Webhook", e)
            false
        }
    }
}
