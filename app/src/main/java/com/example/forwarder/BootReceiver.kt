package com.example.forwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device rebooted/powered on. Checking missed SMS & offline queue...")
            SmsSyncHelper.syncMissedSms(context)
            if (ForwarderEngine.isNetworkAvailable(context)) {
                ForwarderEngine.flushPendingMessages(context)
            }
        }
    }
}
