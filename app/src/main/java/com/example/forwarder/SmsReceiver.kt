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
            Log.d("SmsReceiver", "Incoming SMS from $sender: $body")
            ForwarderEngine.forwardMessage(
                context = context,
                source = "SIM SMS",
                sender = sender,
                content = body
            )
        }
    }
}
