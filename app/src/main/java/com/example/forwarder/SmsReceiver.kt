package com.example.forwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val sender = messages[0].originatingAddress ?: "Unknown SMS"
            val fullBody = StringBuilder()
            for (sms in messages) {
                fullBody.append(sms.messageBody ?: "")
            }

            val body = fullBody.toString()
            val timestamp = messages[0].timestampMillis
            SmsSyncHelper.updateLastProcessedTimestamp(context, timestamp)

            // SIM SMS Sender Filter Check
            val prefs = ForwarderEngine.getPrefs(context)
            val isSmsFilterEnabled = prefs.getBoolean("enable_sms_filter", true)
            if (isSmsFilterEnabled) {
                val allowedSendersRaw = prefs.getString("sms_sender_filter", "bKash, NAGAD, upay, 16216") ?: "bKash, NAGAD, upay, 16216"
                val keywords = allowedSendersRaw.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                val senderLower = sender.lowercase()
                val isAllowed = keywords.any { kw -> senderLower.contains(kw) }
                if (!isAllowed) {
                    Log.d("SmsReceiver", "Ignored SMS from $sender (Not in allowed senders filter: $allowedSendersRaw)")
                    return
                }
            }

            // Deduplication Check
            if (SmsTracker.isAlreadyProcessed(context, sender, body)) {
                Log.d("SmsReceiver", "Ignored duplicate live SMS from $sender")
                return
            }
            SmsTracker.markAsProcessed(context, sender, body)

            Log.d("SmsReceiver", "Incoming SMS from $sender: $body")
            ForwarderEngine.forwardMessage(
                context = context,
                source = "SMS",
                sender = sender,
                content = body
            )
        }
    }
}
