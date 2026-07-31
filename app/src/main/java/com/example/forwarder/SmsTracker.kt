package com.example.forwarder

import android.content.Context

object SmsTracker {
    private const val PREFS_NAME = "ForwarderPrefs"
    private const val KEY_PROCESSED_SET = "processed_sms_unique_keys"

    @Synchronized
    fun isAlreadyProcessed(context: Context, sender: String, content: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_PROCESSED_SET, emptySet()) ?: emptySet()
        val key = generateKey(sender, content)
        return set.contains(key)
    }

    @Synchronized
    fun markAsProcessed(context: Context, sender: String, content: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = (prefs.getStringSet(KEY_PROCESSED_SET, emptySet()) ?: emptySet()).toMutableSet()
        val key = generateKey(sender, content)
        set.add(key)

        // Keep maximum 100 recent processed SMS keys to prevent memory growth (< 2 KB)
        if (set.size > 100) {
            val trimmedSet = set.toList().takeLast(50).toSet()
            prefs.edit().putStringSet(KEY_PROCESSED_SET, trimmedSet).apply()
        } else {
            prefs.edit().putStringSet(KEY_PROCESSED_SET, set).apply()
        }
    }

    private fun generateKey(sender: String, content: String): String {
        val s = sender.trim().lowercase()
        val c = content.trim()
        return "$s|${c.hashCode()}"
    }
}
