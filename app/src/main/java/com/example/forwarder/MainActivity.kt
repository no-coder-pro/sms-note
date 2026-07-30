package com.example.forwarder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var layoutSavedCredentials: LinearLayout
    private lateinit var layoutEditCredentials: LinearLayout
    private lateinit var tvSavedSummary: TextView
    private lateinit var btnEditConfig: Button
    private lateinit var btnCancelEdit: Button

    private lateinit var etTgBotToken: EditText
    private lateinit var etTgChatId: EditText
    private lateinit var etSupabaseUrl: EditText
    private lateinit var etSupabaseKey: EditText
    private lateinit var etEmailWebhook: EditText
    private lateinit var btnSaveConfig: Button

    private lateinit var rgFilterMode: RadioGroup
    private lateinit var rbFilterAll: RadioButton
    private lateinit var rbFilterSms: RadioButton
    private lateinit var rbFilterNotification: RadioButton

    private lateinit var cbEnableTelegram: CheckBox
    private lateinit var cbEnableSupabase: CheckBox
    private lateinit var cbEnableWebhook: CheckBox
    private lateinit var etAppFilter: EditText
    private lateinit var btnSelectApps: Button
    private lateinit var tvSelectedAppsSummary: TextView

    private lateinit var btnGrantSmsPermission: Button
    private lateinit var btnGrantNotificationPermission: Button
    private lateinit var btnDisableBatteryOpt: Button
    private lateinit var tvPendingCount: TextView
    private lateinit var tvStatus: TextView

    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadSavedConfig()
        setupListeners()
        updatePendingCount()
    }

    override fun onResume() {
        super.onResume()
        updatePendingCount()
    }

    private fun initViews() {
        layoutSavedCredentials = findViewById(R.id.layoutSavedCredentials)
        layoutEditCredentials = findViewById(R.id.layoutEditCredentials)
        tvSavedSummary = findViewById(R.id.tvSavedSummary)
        btnEditConfig = findViewById(R.id.btnEditConfig)
        btnCancelEdit = findViewById(R.id.btnCancelEdit)

        etTgBotToken = findViewById(R.id.etTgBotToken)
        etTgChatId = findViewById(R.id.etTgChatId)
        etSupabaseUrl = findViewById(R.id.etSupabaseUrl)
        etSupabaseKey = findViewById(R.id.etSupabaseKey)
        etEmailWebhook = findViewById(R.id.etEmailWebhook)
        btnSaveConfig = findViewById(R.id.btnSaveConfig)

        cbEnableTelegram = findViewById(R.id.cbEnableTelegram)
        cbEnableSupabase = findViewById(R.id.cbEnableSupabase)
        cbEnableWebhook = findViewById(R.id.cbEnableWebhook)

        rgFilterMode = findViewById(R.id.rgFilterMode)
        rbFilterAll = findViewById(R.id.rbFilterAll)
        rbFilterSms = findViewById(R.id.rbFilterSms)
        rbFilterNotification = findViewById(R.id.rbFilterNotification)

        btnSelectApps = findViewById(R.id.btnSelectApps)
        tvSelectedAppsSummary = findViewById(R.id.tvSelectedAppsSummary)
        etAppFilter = findViewById(R.id.etAppFilter)

        btnGrantSmsPermission = findViewById(R.id.btnGrantSmsPermission)
        btnGrantNotificationPermission = findViewById(R.id.btnGrantNotificationPermission)
        btnDisableBatteryOpt = findViewById(R.id.btnDisableBatteryOpt)
        tvPendingCount = findViewById(R.id.tvPendingCount)
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun loadSavedConfig() {
        val prefs = ForwarderEngine.getPrefs(this)
        val tgToken = prefs.getString("tg_bot_token", "") ?: ""
        val tgChatId = prefs.getString("tg_chat_id", "") ?: ""
        val supabaseUrl = prefs.getString("supabase_url", "") ?: ""
        val supabaseKey = prefs.getString("supabase_key", "") ?: ""
        val emailWebhook = prefs.getString("email_webhook", "") ?: ""

        etTgBotToken.setText(tgToken)
        etTgChatId.setText(tgChatId)
        etSupabaseUrl.setText(supabaseUrl)
        etSupabaseKey.setText(supabaseKey)
        etEmailWebhook.setText(emailWebhook)

        cbEnableTelegram.isChecked = prefs.getBoolean("enable_telegram", true)
        cbEnableSupabase.isChecked = prefs.getBoolean("enable_supabase", true)
        cbEnableWebhook.isChecked = prefs.getBoolean("enable_webhook", true)

        etAppFilter.setText(prefs.getString("app_filter", ""))
        updateSelectedAppsSummary()

        val hasSavedConfig = tgToken.isNotBlank() || supabaseUrl.isNotBlank() || emailWebhook.isNotBlank()
        if (hasSavedConfig) {
            updateCompactSummary(tgToken, tgChatId, supabaseUrl, emailWebhook)
            layoutSavedCredentials.visibility = View.VISIBLE
            layoutEditCredentials.visibility = View.GONE
            btnCancelEdit.visibility = View.VISIBLE
        } else {
            layoutSavedCredentials.visibility = View.GONE
            layoutEditCredentials.visibility = View.VISIBLE
            btnCancelEdit.visibility = View.GONE
        }

        val filterMode = prefs.getString("filter_mode", "ALL") ?: "ALL"
        when (filterMode) {
            "SMS_ONLY" -> rbFilterSms.isChecked = true
            "NOTIFICATION_ONLY" -> rbFilterNotification.isChecked = true
            else -> rbFilterAll.isChecked = true
        }
    }

    private fun updateSelectedAppsSummary() {
        val prefs = ForwarderEngine.getPrefs(this)
        val rawSelected = prefs.getString("selected_app_packages", "") ?: ""
        if (rawSelected.isBlank()) {
            tvSelectedAppsSummary.text = "Target Apps: All Apps Allowed (Default)"
            tvSelectedAppsSummary.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            val count = rawSelected.split(",").filter { it.isNotBlank() }.size
            tvSelectedAppsSummary.text = "Target Apps: $count App(s) Selected"
            tvSelectedAppsSummary.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
        }
    }

    private fun showAppPickerDialog() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        if (resolveInfos.isEmpty()) {
            Toast.makeText(this, "No launchable apps found", Toast.LENGTH_SHORT).show()
            return
        }

        val appNames = resolveInfos.map { it.loadLabel(pm).toString() }.toTypedArray()
        val appPackages = resolveInfos.map { it.activityInfo.packageName }

        val prefs = ForwarderEngine.getPrefs(this)
        val savedPackages = (prefs.getString("selected_app_packages", "") ?: "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        val dialogView = layoutInflater.inflate(R.layout.dialog_app_picker, null)
        val cbSelectAllApps = dialogView.findViewById<CheckBox>(R.id.cbSelectAllApps)
        val lvAppList = dialogView.findViewById<ListView>(R.id.lvAppList)
        val btnSaveAppSelection = dialogView.findViewById<Button>(R.id.btnSaveAppSelection)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, appNames)
        lvAppList.adapter = adapter

        // Pre-check saved items
        var initialCheckedCount = 0
        for (i in appPackages.indices) {
            if (savedPackages.contains(appPackages[i])) {
                lvAppList.setItemChecked(i, true)
                initialCheckedCount++
            }
        }

        // Set initial state of Select All checkbox
        cbSelectAllApps.isChecked = (initialCheckedCount == appPackages.size && appPackages.isNotEmpty())

        // Top Select All / Unselect All checkbox listener
        cbSelectAllApps.setOnClickListener {
            val isChecked = cbSelectAllApps.isChecked
            for (i in appPackages.indices) {
                lvAppList.setItemChecked(i, isChecked)
            }
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnSaveAppSelection.setOnClickListener {
            val selected = mutableListOf<String>()
            val checkedPositions = lvAppList.checkedItemPositions
            for (i in appPackages.indices) {
                if (checkedPositions.get(i)) {
                    selected.add(appPackages[i])
                }
            }
            val packageStr = selected.joinToString(",")
            ForwarderEngine.getPrefs(this).edit().putString("selected_app_packages", packageStr).apply()
            updateSelectedAppsSummary()
            Toast.makeText(this, "Saved ${selected.size} target apps!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateCompactSummary(tgToken: String, tgChatId: String, supabaseUrl: String, emailWebhook: String) {
        val maskedToken = if (tgToken.length > 6) "••••${tgToken.takeLast(4)}" else if (tgToken.isNotBlank()) "Configured" else "Not set"
        val maskedChatId = if (tgChatId.isNotBlank()) tgChatId else "Not set"
        val sbStatus = if (supabaseUrl.isNotBlank()) "Configured (${supabaseUrl.take(20)}...)" else "Not set"
        val webhookStatus = if (emailWebhook.isNotBlank()) "Configured" else "Not set"
        tvSavedSummary.text = "• Telegram Bot: $maskedToken (Chat ID: $maskedChatId)\n• Supabase: $sbStatus\n• Webhook: $webhookStatus"
    }

    private fun setupListeners() {
        btnSelectApps.setOnClickListener {
            showAppPickerDialog()
        }

        btnSaveConfig.setOnClickListener {
            val token = etTgBotToken.text.toString().trim()
            val chatId = etTgChatId.text.toString().trim()
            val sbUrl = etSupabaseUrl.text.toString().trim()
            val sbKey = etSupabaseKey.text.toString().trim()
            val webhook = etEmailWebhook.text.toString().trim()
            val appFilter = etAppFilter.text.toString().trim()

            val prefs = ForwarderEngine.getPrefs(this).edit()
            prefs.putString("tg_bot_token", token)
            prefs.putString("tg_chat_id", chatId)
            prefs.putString("supabase_url", sbUrl)
            prefs.putString("supabase_key", sbKey)
            prefs.putString("email_webhook", webhook)

            prefs.putBoolean("enable_telegram", cbEnableTelegram.isChecked)
            prefs.putBoolean("enable_supabase", cbEnableSupabase.isChecked)
            prefs.putBoolean("enable_webhook", cbEnableWebhook.isChecked)
            prefs.putString("app_filter", appFilter)
            prefs.apply()

            updateCompactSummary(token, chatId, sbUrl, webhook)
            layoutSavedCredentials.visibility = View.VISIBLE
            layoutEditCredentials.visibility = View.GONE
            btnCancelEdit.visibility = View.VISIBLE

            Toast.makeText(this, "Configuration Saved!", Toast.LENGTH_SHORT).show()
            tvStatus.text = "Status: Configuration Saved"
        }

        val destinationToggleListener = { _: View ->
            val prefs = ForwarderEngine.getPrefs(this).edit()
            prefs.putBoolean("enable_telegram", cbEnableTelegram.isChecked)
            prefs.putBoolean("enable_supabase", cbEnableSupabase.isChecked)
            prefs.putBoolean("enable_webhook", cbEnableWebhook.isChecked)
            prefs.apply()
        }
        cbEnableTelegram.setOnClickListener(destinationToggleListener)
        cbEnableSupabase.setOnClickListener(destinationToggleListener)
        cbEnableWebhook.setOnClickListener(destinationToggleListener)

        btnEditConfig.setOnClickListener {
            layoutSavedCredentials.visibility = View.GONE
            layoutEditCredentials.visibility = View.VISIBLE
            tvStatus.text = "Status: Editing Credentials"
        }

        btnCancelEdit.setOnClickListener {
            loadSavedConfig()
            tvStatus.text = "Status: Ready"
        }

        rgFilterMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbFilterSms -> "SMS_ONLY"
                R.id.rbFilterNotification -> "NOTIFICATION_ONLY"
                else -> "ALL"
            }
            ForwarderEngine.getPrefs(this).edit().putString("filter_mode", mode).apply()
            val modeName = when (mode) {
                "SMS_ONLY" -> "Only SIM SMS"
                "NOTIFICATION_ONLY" -> "Notification Only"
                else -> "All (SMS & Notifications)"
            }
            Toast.makeText(this, "Filter mode set to: $modeName", Toast.LENGTH_SHORT).show()
        }

        btnGrantSmsPermission.setOnClickListener {
            requestSmsPermissions()
        }

        btnGrantNotificationPermission.setOnClickListener {
            openNotificationListenerSettings()
        }

        btnDisableBatteryOpt.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }
    }

    private fun updatePendingCount() {
        val dbHelper = DatabaseHelper(this)
        val count = dbHelper.getPendingCount()
        tvPendingCount.text = "Offline Unsent Queue: $count messages"
    }

    private fun requestSmsPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            Toast.makeText(this, "SMS Permissions already granted!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openNotificationListenerSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open Notification Access settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Unable to open Battery Optimization settings", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Battery Optimizations already disabled!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Battery Optimization setting not required for Android < 6.0", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "SMS Permissions Granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions Denied!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

