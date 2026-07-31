package com.example.forwarder

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        // Exclude self notifications to prevent infinite loops
        if (packageName == applicationContext.packageName) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString()
            ?: extras.getCharSequence("android.title.big")?.toString()
            ?: "No Title"

        // Extract complete notification body (bKash/Nagad put detailed transaction body in android.bigText)
        var text = extras.getCharSequence("android.bigText")?.toString() ?: ""
        if (text.isBlank()) {
            text = extras.getCharSequence("android.text")?.toString() ?: ""
        }
        if (text.isBlank()) {
            val lines = extras.getCharSequenceArray("android.textLines")
            if (!lines.isNullOrEmpty()) {
                text = lines.joinToString("\n")
            }
        }
        if (text.isBlank()) {
            text = extras.getCharSequence("android.subText")?.toString() ?: ""
        }

        // Ignore empty notification text or system alerts
        if (text.isBlank() || title.isBlank()) return

        // Selected Installed Apps Filter check
        val prefs = ForwarderEngine.getPrefs(applicationContext)
        val selectedPackages = (prefs.getString("selected_app_packages", "") ?: "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (selectedPackages.isNotEmpty() && !selectedPackages.contains(packageName)) {
            Log.d("NotificationService", "Ignored notification from $packageName (Not in selected target apps list)")
            return
        }

        Log.d("NotificationService", "Notification from $packageName: $title - $text")

        ForwarderEngine.forwardMessage(
            context = applicationContext,
            source = "App: $packageName",
            sender = title,
            content = text
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("NotificationService", "Notification Listener Connected! Checking offline queue...")
        if (ForwarderEngine.isNetworkAvailable(applicationContext)) {
            ForwarderEngine.flushPendingMessages(applicationContext)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
