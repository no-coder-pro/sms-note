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

        // Selected Installed Apps Filter check
        val prefs = ForwarderEngine.getPrefs(applicationContext)
        val selectedPackages = (prefs.getString("selected_app_packages", "") ?: "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (selectedPackages.isNotEmpty() && !selectedPackages.contains(packageName)) {
            Log.d("NotificationService", "Ignored notification from $packageName (Not in selected apps list)")
            return
        }

        // Custom Keyword Filter check
        val appFilter = prefs.getString("app_filter", "") ?: ""
        if (appFilter.isNotBlank()) {
            val keywords = appFilter.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val packageLower = packageName.lowercase()
            val titleLower = title.lowercase()
            val matches = keywords.any { kw -> packageLower.contains(kw) || titleLower.contains(kw) }
            if (!matches) {
                Log.d("NotificationService", "Filtered out notification from $packageName ($title) based on keyword filter [$appFilter]")
                return
            }
        }

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
