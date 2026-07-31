package com.example.forwarder

import android.content.Context
import android.net.Uri
import android.util.Log

object SmsSyncHelper {
    private const val TAG = "SmsSyncHelper"

    fun syncMissedSms(context: Context) {
        val prefs = ForwarderEngine.getPrefs(context)
        // Default to last 2 hours if no timestamp is saved yet
        val defaultTime = System.currentTimeMillis() - (2 * 60 * 60 * 1000)
        val lastTimestamp = prefs.getLong("last_processed_sms_timestamp", defaultTime)

        try {
            val contentResolver = context.contentResolver
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id", "address", "body", "date")
            val selection = "date > ?"
            val selectionArgs = arrayOf(lastTimestamp.toString())
            val sortOrder = "date ASC"

            val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)

            cursor?.use {
                var maxDate = lastTimestamp
                var count = 0

                while (it.moveToNext()) {
                    val address = it.getString(it.getColumnIndexOrThrow("address")) ?: "Unknown SMS"
                    val body = it.getString(it.getColumnIndexOrThrow("body")) ?: ""
                    val date = it.getLong(it.getColumnIndexOrThrow("date"))

                    if (date > maxDate) {
                        maxDate = date
                    }

                    if (body.isNotBlank()) {
                        val isSmsFilterEnabled = prefs.getBoolean("enable_sms_filter", true)
                        if (isSmsFilterEnabled) {
                            val allowedSendersRaw = prefs.getString("sms_sender_filter", "bKash, NAGAD, upay, 16216") ?: "bKash, NAGAD, upay, 16216"
                            val keywords = allowedSendersRaw.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                            val addressLower = address.lowercase()
                            val isAllowed = keywords.any { kw -> addressLower.contains(kw) }
                            if (!isAllowed) {
                                Log.d(TAG, "Ignored missed SMS from $address (Not in allowed senders filter)")
                                continue
                            }
                        }

                        count++
                        Log.d(TAG, "Syncing missed SMS from $address (date: $date): $body")
                        ForwarderEngine.forwardMessage(
                            context = context,
                            source = "SIM SMS (Missed)",
                            sender = address,
                            content = body
                        )
                    }
                }

                if (maxDate > lastTimestamp) {
                    prefs.edit().putLong("last_processed_sms_timestamp", maxDate).apply()
                    Log.d(TAG, "Synced $count missed SMS. Updated last_processed_sms_timestamp to $maxDate")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing missed SMS from Inbox content provider", e)
        }
    }

    fun updateLastProcessedTimestamp(context: Context, timestamp: Long = System.currentTimeMillis()) {
        val prefs = ForwarderEngine.getPrefs(context)
        val current = prefs.getLong("last_processed_sms_timestamp", 0L)
        if (timestamp > current) {
            prefs.edit().putLong("last_processed_sms_timestamp", timestamp).apply()
        }
    }
}
