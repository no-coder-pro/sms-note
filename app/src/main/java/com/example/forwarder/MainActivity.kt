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
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etTgBotToken: EditText
    private lateinit var etTgChatId: EditText
    private lateinit var etSupabaseUrl: EditText
    private lateinit var etSupabaseKey: EditText
    private lateinit var etEmailWebhook: EditText
    private lateinit var btnSaveConfig: Button
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
        etTgBotToken = findViewById(R.id.etTgBotToken)
        etTgChatId = findViewById(R.id.etTgChatId)
        etSupabaseUrl = findViewById(R.id.etSupabaseUrl)
        etSupabaseKey = findViewById(R.id.etSupabaseKey)
        etEmailWebhook = findViewById(R.id.etEmailWebhook)
        btnSaveConfig = findViewById(R.id.btnSaveConfig)
        btnGrantSmsPermission = findViewById(R.id.btnGrantSmsPermission)
        btnGrantNotificationPermission = findViewById(R.id.btnGrantNotificationPermission)
        btnDisableBatteryOpt = findViewById(R.id.btnDisableBatteryOpt)
        tvPendingCount = findViewById(R.id.tvPendingCount)
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun loadSavedConfig() {
        val prefs = ForwarderEngine.getPrefs(this)
        etTgBotToken.setText(prefs.getString("tg_bot_token", ""))
        etTgChatId.setText(prefs.getString("tg_chat_id", ""))
        etSupabaseUrl.setText(prefs.getString("supabase_url", ""))
        etSupabaseKey.setText(prefs.getString("supabase_key", ""))
        etEmailWebhook.setText(prefs.getString("email_webhook", ""))
    }

    private fun setupListeners() {
        btnSaveConfig.setOnClickListener {
            val prefs = ForwarderEngine.getPrefs(this).edit()
            prefs.putString("tg_bot_token", etTgBotToken.text.toString().trim())
            prefs.putString("tg_chat_id", etTgChatId.text.toString().trim())
            prefs.putString("supabase_url", etSupabaseUrl.text.toString().trim())
            prefs.putString("supabase_key", etSupabaseKey.text.toString().trim())
            prefs.putString("email_webhook", etEmailWebhook.text.toString().trim())
            prefs.apply()

            Toast.makeText(this, "Configuration Saved!", Toast.LENGTH_SHORT).show()
            tvStatus.text = "Status: Configuration Saved"
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
