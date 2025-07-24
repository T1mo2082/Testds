package com.example.voicecommander

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.voicecommander.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requiredPermissions = arrayOf(Manifest.permission.RECORD_AUDIO)

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.entries.all { it.value }) {
                startVoiceService()
            } else {
                Toast.makeText(this, "Audio permission is required to listen for commands.", Toast.LENGTH_LONG).show()
            }
        }
    
    private val overlayPermissionLauncher = 
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                checkPermissionsAndStartService()
            } else {
                Toast.makeText(this, "Draw Over Other Apps permission is required for the floating button.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.accessibilitySettingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        binding.toggleServiceButton.setOnClickListener {
            toggleService()
        }
    }

    override fun onResume() {
        super.onResume()
        updateButtonState()
    }

    private fun toggleService() {
        if (isServiceRunning(VoiceCommandService::class.java)) {
            stopService(Intent(this, VoiceCommandService::class.java))
            Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
        } else {
            checkPermissionsAndStartService()
        }
        // A small delay to allow service state to update before refreshing the button
        binding.root.postDelayed({ updateButtonState() }, 200)
    }

    private fun checkPermissionsAndStartService() {
        when {
            !isAccessibilityServiceEnabled() -> {
                Toast.makeText(this, "Please enable the Accessibility Service first.", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            !Settings.canDrawOverlays(this) -> {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                overlayPermissionLauncher.launch(intent)
            }
            !hasRequiredPermissions() -> {
                requestPermissionsLauncher.launch(requiredPermissions)
            }
            else -> {
                startVoiceService()
            }
        }
    }
    
    private fun startVoiceService() {
        val intent = Intent(this, VoiceCommandService::class.java)
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show()
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceId = "$packageName/${GestureAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(serviceId) == true
    }
    
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
    
    private fun updateButtonState() {
        if (isServiceRunning(VoiceCommandService::class.java)) {
            binding.toggleServiceButton.text = getString(R.string.button_stop_service)
        } else {
            binding.toggleServiceButton.text = getString(R.string.button_start_service)
        }
    }
}
