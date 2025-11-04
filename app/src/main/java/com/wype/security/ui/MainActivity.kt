package com.wype.security.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wype.security.R
import com.wype.security.databinding.ActivityMainBinding
import com.wype.security.ui.auth.LoginActivity
import com.wype.security.utils.PreferencesManager
import com.wype.security.service.SilentSpeechService
import com.wype.security.service.ProtectionModeManager
import com.wype.security.utils.BatteryOptimizationHelper
import com.wype.security.utils.PermissionManager
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionManager: PermissionManager
    private lateinit var protectionModeManager: ProtectionModeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle screen wake for emergency situations
        handleEmergencyScreenWake()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize managers
        permissionManager = PermissionManager(this)
        protectionModeManager = ProtectionModeManager.getInstance(this)
        
        // Check and request permissions first
        checkAndRequestPermissions()
        
        // Check if we need to automatically start protection mode after setup
        checkAutoStartProtectionMode()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        
        // Set up custom navigation buttons
        setupCustomNavigation(navController)
        
        // Start background speech service (after permissions are checked)
        startSpeechListenerService()
    }
    
    private fun setupCustomNavigation(navController: NavController) {
        android.util.Log.d("NavTest", "Setting up custom navigation")
        
        // Home button
        binding.btnNavHome.setOnClickListener {
            android.util.Log.d("NavTest", "Home button clicked")
            navController.navigate(R.id.navigation_home)
            updateButtonStates(R.id.navigation_home)
        }
        
        // Instructions button  
        binding.btnNavInstructions.setOnClickListener {
            android.util.Log.d("NavTest", "Instructions button clicked")
            navController.navigate(R.id.navigation_instructions)
            updateButtonStates(R.id.navigation_instructions)
        }
        
        // Record button
        binding.btnNavRecord.setOnClickListener {
            android.util.Log.d("NavTest", "Record button clicked")
            navController.navigate(R.id.navigation_record)
            updateButtonStates(R.id.navigation_record)
        }
        
        // Buddy button
        binding.btnNavBuddy.setOnClickListener {
            android.util.Log.d("NavTest", "Buddy button clicked")
            navController.navigate(R.id.navigation_buddy)
            updateButtonStates(R.id.navigation_buddy)
        }
        
        // Backup button
        binding.btnNavBackup.setOnClickListener {
            android.util.Log.d("NavTest", "Backup button clicked")
            navController.navigate(R.id.navigation_backup)
            updateButtonStates(R.id.navigation_backup)
        }
        
        // Listen for destination changes to update button states
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateButtonStates(destination.id)
        }
    }
    
    private fun updateButtonStates(currentDestination: Int) {
        // Optional: Add subtle visual feedback for active button
        // Reset all button alphas
        listOf(
            binding.btnNavHome,
            binding.btnNavInstructions,
            binding.btnNavRecord,
            binding.btnNavBuddy,
            binding.btnNavBackup
        ).forEach { button ->
            button.alpha = 0.7f // Slightly transparent when inactive
        }
        
        // Highlight current button (make it fully opaque)
        when (currentDestination) {
            R.id.navigation_home -> binding.btnNavHome.alpha = 1.0f
            R.id.navigation_instructions -> binding.btnNavInstructions.alpha = 1.0f
            R.id.navigation_record -> binding.btnNavRecord.alpha = 1.0f
            R.id.navigation_buddy -> binding.btnNavBuddy.alpha = 1.0f
            R.id.navigation_backup -> binding.btnNavBackup.alpha = 1.0f
        }
    }
    
    private fun startSpeechListenerService() {
        try {
            val preferencesManager = PreferencesManager(this)
            
            // For testing: start service regardless of settings
            // Only start service if it's enabled and wake phrase is configured
            // if (preferencesManager.isServiceEnabled()) {
            if (true) {
                
                // Check and request battery optimization exemption
                if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
                    BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this)
                }
                
                // Use the new SilentSpeechService for wake word detection
                val serviceIntent = Intent(this, SilentSpeechService::class.java).apply {
                    action = SilentSpeechService.ACTION_START_LISTENING
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        } catch (e: Exception) {
            // Service start failed - continue normally
            Toast.makeText(this, "Failed to start wake word service: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleEmergencyScreenWake() {
        // Handle screen wake for emergency phrase detection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Android 8.1+ way to show on lock screen and turn on screen
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            // Older Android way
            window?.apply {
                @Suppress("DEPRECATION")
                addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                @Suppress("DEPRECATION")
                addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
                @Suppress("DEPRECATION")
                addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            }
        }
    }
    
    /**
     * Check and request all required permissions
     */
    private fun checkAndRequestPermissions() {
        // Handle specific microphone permission request from service
        if (intent.getBooleanExtra("REQUEST_MIC_PERMISSION", false)) {
            showMicrophonePermissionDialog()
            return
        }
        
        // Check if all permissions are granted
        if (!permissionManager.hasAllRequiredPermissions()) {
            showPermissionWelcomeDialog()
        }
    }
    
    /**
     * Show welcome dialog explaining why permissions are needed
     */
    private fun showPermissionWelcomeDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Welcome to Wype Security")
            .setMessage("To protect your device, Wype needs several permissions:\n\n" +
                "🎤 Microphone - Listen for your emergency phrase\n" +
                "📱 SMS - Send emergency alerts to your contacts\n" +
                "📍 Location - Include location in emergency messages\n" +
                "🔒 Device Admin - Perform emergency factory reset\n" +
                "🔋 Battery - Keep running in background\n\n" +
                "We'll guide you through granting these permissions.")
            .setPositiveButton("Grant Permissions") { _, _ ->
                permissionManager.requestAllPermissions()
            }
            .setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Some features may not work without permissions", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Show specific microphone permission dialog
     */
    private fun showMicrophonePermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Microphone Permission Lost")
            .setMessage("Wype has lost access to the microphone. This is required to listen for your emergency phrase. Please grant microphone permission to continue protecting your device.")
            .setPositiveButton("Grant Permission") { _, _ ->
                permissionManager.requestAllPermissions()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Handle permission request results
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            // Permission result was handled by permission manager
            checkPermissionStatus()
        }
    }
    
    /**
     * Handle activity results (for device admin, overlay permissions, etc.)
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (permissionManager.onActivityResult(requestCode, resultCode, data)) {
            // Activity result was handled by permission manager
            checkPermissionStatus()
        }
    }
    
    /**
     * Check current permission status and show appropriate feedback
     */
    private fun checkPermissionStatus() {
        if (permissionManager.hasAllRequiredPermissions()) {
            Toast.makeText(this, "All permissions granted! Wype is now fully protected.", Toast.LENGTH_LONG).show()
            // Restart the speech service if needed
            startSpeechListenerService()
        } else {
            val missing = permissionManager.getMissingPermissionsSummary()
            Toast.makeText(this, missing, Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Check if protection mode should be automatically started after setup completion
     */
    private fun checkAutoStartProtectionMode() {
        try {
            val preferencesManager = PreferencesManager(this)
            
            // Check if all required components are configured for protection mode
            val hasWakePhrase = !preferencesManager.getWakePhrase().isNullOrEmpty()
            val hasEmergencyContact = !preferencesManager.getBuddyPhone().isNullOrEmpty()
            val isProtectionModeEnabled = preferencesManager.isProtectionModeEnabled()
            
            // Auto-start protection mode if:
            // 1. All requirements are met
            // 2. Protection mode was previously enabled but service isn't running
            // 3. User completed setup during previous session
            if (hasWakePhrase && hasEmergencyContact && isProtectionModeEnabled) {
                android.util.Log.i("MainActivity", "Auto-starting protection mode - all requirements met")
                
                // Start protection mode in background
                protectionModeManager.enterProtectionMode(preferencesManager.getWakePhrase() ?: "")
                
                Toast.makeText(this, "🛡️ Protection Mode Auto-Started", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking auto-start protection mode", e)
        }
    }
    
    /**
     * Called when returning from settings or other activities
     */
    override fun onResume() {
        super.onResume()
        
        // Check if permissions were granted while away
        if (::permissionManager.isInitialized) {
            checkPermissionStatus()
        }
        
        // Re-check protection mode auto-start in case user completed setup
        if (::protectionModeManager.isInitialized) {
            checkAutoStartProtectionMode()
        }
    }
}
