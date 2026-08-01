package com.example.forwarder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

object SimUtils {

    fun getSimLabel(context: Context, subId: Int = -1, slotId: Int = -1): String {
        var sim1CustomNum = ""
        var sim2CustomNum = ""

        try {
            val prefs = ForwarderEngine.getPrefs(context)
            sim1CustomNum = prefs.getString("sim_1_number", "")?.trim() ?: ""
            sim2CustomNum = prefs.getString("sim_2_number", "")?.trim() ?: ""

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {

                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val activeList = sm?.activeSubscriptionInfoList

                if (!activeList.isNullOrEmpty()) {
                    for (info in activeList) {
                        val matchSub = (subId != -1 && info.subscriptionId == subId)
                        val matchSlot = (slotId != -1 && info.simSlotIndex == slotId)

                        if (matchSub || matchSlot) {
                            val slot = info.simSlotIndex

                            // 1. Try Auto Detection first
                            val num = info.number?.trim() ?: ""
                            if (num.isNotBlank()) return num

                            try {
                                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                                val line1 = tm?.line1Number?.trim() ?: ""
                                if (line1.isNotBlank()) return line1
                            } catch (_: Exception) {}

                            // 2. Fallback to user manually typed custom number if auto detection fails
                            val customNum = if (slot == 0) sim1CustomNum else sim2CustomNum
                            if (customNum.isNotBlank()) {
                                return customNum
                            }

                            // 3. Fallback to carrier name
                            val carrier = info.displayName?.toString()?.trim() ?: ""
                            return when {
                                carrier.isNotBlank() -> "SIM ${slot + 1} ($carrier)"
                                else -> "SIM ${slot + 1}"
                            }
                        }
                    }

                    if (activeList.size == 1) {
                        val info = activeList[0]
                        val num = info.number?.trim() ?: ""
                        if (num.isNotBlank()) return num

                        try {
                            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                            val line1 = tm?.line1Number?.trim() ?: ""
                            if (line1.isNotBlank()) return line1
                        } catch (_: Exception) {}

                        val customNum = if (info.simSlotIndex == 0) sim1CustomNum else sim2CustomNum
                        if (customNum.isNotBlank()) return customNum

                        val carrier = info.displayName?.toString()?.trim() ?: ""
                        if (carrier.isNotBlank()) return "SIM ${info.simSlotIndex + 1} ($carrier)"
                    }
                }
            }

            if (slotId == 0 && sim1CustomNum.isNotBlank()) return sim1CustomNum
            if (slotId == 1 && sim2CustomNum.isNotBlank()) return sim2CustomNum

            if (sim1CustomNum.isNotBlank()) return sim1CustomNum

        } catch (e: Exception) {
            // Fallback gracefully
        }

        return if (sim1CustomNum.isNotBlank()) sim1CustomNum else "SIM 1"
    }
}
