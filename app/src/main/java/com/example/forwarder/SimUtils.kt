package com.example.forwarder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

object SimUtils {

    fun getSimLabel(context: Context, subId: Int = -1, slotId: Int = -1): String {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                return if (slotId >= 0) "SIM ${slotId + 1}" else if (subId >= 0) "SIM ${subId + 1}" else "SIM"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LPOLLIPOP_MR1) {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val activeList = sm?.activeSubscriptionInfoList
                if (!activeList.isNullOrEmpty()) {
                    for (info in activeList) {
                        val matchSub = (subId != -1 && info.subscriptionId == subId)
                        val matchSlot = (slotId != -1 && info.simSlotIndex == slotId)
                        if (matchSub || matchSlot) {
                            val slot = info.simSlotIndex + 1
                            val num = info.number?.trim() ?: ""
                            val carrier = info.displayName?.toString()?.trim() ?: ""
                            return when {
                                num.isNotBlank() -> num
                                carrier.isNotBlank() -> "SIM $slot ($carrier)"
                                else -> "SIM $slot"
                            }
                        }
                    }
                    if (slotId >= 0) return "SIM ${slotId + 1}"
                    if (subId >= 0) return "SIM ${subId + 1}"
                }
            }
        } catch (e: Exception) {
            // Fallback gracefully
        }
        return if (slotId >= 0) "SIM ${slotId + 1}" else if (subId >= 0) "SIM ${subId + 1}" else "SIM"
    }
}
