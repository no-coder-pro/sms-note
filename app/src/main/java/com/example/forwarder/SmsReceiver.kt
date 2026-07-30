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
            for (sms in messages) {
                val sender = sms.originatingAddress ?: "Unknown SMS"
                val body = sms.messageBody ?: ""

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
}
