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
        val title = extras.getString("android.title") ?: "No Title"
        val text = extras.getCharSequence("android.text")?.toString() ?: "No Content"

        // Ignore empty notification text or system alerts
        if (text.isBlank() || title.isBlank()) return

        Log.d("NotificationService", "Notification from $packageName: $title - $text")

        ForwarderEngine.forwardMessage(
            context = applicationContext,
            source = "App: $packageName",
            sender = title,
            content = text
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
