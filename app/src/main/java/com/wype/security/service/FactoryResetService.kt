package com.wype.security.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.annotation.Nullable
import com.wype.security.utils.PreferencesManager
import kotlinx.coroutines.*

/**
 * Factory Reset Service
 * 
 * Securely performs factory reset as part of emergency protection:
 * - Validates emergency conditions
 * - Performs final data cleanup
 * - Executes factory reset through device admin or system methods
 * - Logs security events before reset
 */
class FactoryResetService : Service() {
    
    companion object {
        private const val TAG = "FactoryResetService"
        
        // Service Actions
        const val ACTION_FACTORY_RESET = "com.wype.security.FACTORY_RESET"
        
        // Security delays
        private const val FINAL_WARNING_DELAY_MS = 3000L // 3 seconds final warning
    }
    
    private lateinit var preferencesManager: PreferencesManager
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    // Allow factory reset when not in protection mode if explicitly requested
    private var allowWithoutProtectionMode: Boolean = false
    
    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        Log.w(TAG, "Factory Reset Service created - EMERGENCY SECURITY SERVICE")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FACTORY_RESET -> {
                // Read override flag if present
                allowWithoutProtectionMode = intent?.getBooleanExtra("allow_without_protection_mode", false) == true
                if (allowWithoutProtectionMode) {
                    Log.w(TAG, "Override enabled: proceeding without protection mode active")
                }
                performSecureFactoryReset()
            }
        }
        
        return START_NOT_STICKY // Don't restart if killed
    }
    
    @Nullable
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.w(TAG, "Factory Reset Service destroyed")
    }
    
    /**
     * Perform secure factory reset with validation and logging
     */
    private fun performSecureFactoryReset() {
        serviceScope.launch {
            try {
                Log.w(TAG, "EMERGENCY FACTORY RESET INITIATED")
                
                // 1. Validate emergency conditions
                if (!validateEmergencyConditions()) {
                    Log.e(TAG, "Factory reset blocked - invalid emergency conditions")
                    stopSelf()
                    return@launch
                }
                
                // 2. Log security event
                logSecurityEvent()
                
                // 3. Final warning delay
                Log.w(TAG, "FINAL WARNING: Factory reset in ${FINAL_WARNING_DELAY_MS}ms")
                delay(FINAL_WARNING_DELAY_MS)
                
                // 4. Perform final cleanup
                performFinalCleanup()
                
                // 5. Execute factory reset
                executeFactoryReset()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during factory reset process", e)
            } finally {
                stopSelf()
            }
        }
    }
    
    /**
     * Validate that factory reset should proceed
     */
    private fun validateEmergencyConditions(): Boolean {
        try {
            // Check if protection mode is active unless override is set
            if (!preferencesManager.isProtectionModeEnabled() && !allowWithoutProtectionMode) {
                Log.e(TAG, "Protection mode not active - factory reset denied (no override)")
                return false
            } else if (allowWithoutProtectionMode && !preferencesManager.isProtectionModeEnabled()) {
                Log.w(TAG, "Protection mode disabled, but override flag present - continuing")
            }
            
            // Check if wake phrase is configured
            val wakePhrase = preferencesManager.getWakePhrase()
            if (wakePhrase.isNullOrEmpty()) {
                Log.e(TAG, "No wake phrase configured - factory reset denied")
                return false
            }
            
            // Check emergency contact configuration
            val emergencyContact = preferencesManager.getBuddyPhone()
            if (emergencyContact.isNullOrEmpty()) {
                Log.w(TAG, "No emergency contact configured - proceeding anyway")
            }
            
            Log.i(TAG, "Emergency conditions validated - factory reset authorized")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating emergency conditions", e)
            return false
        }
    }
    
    /**
     * Log security event before factory reset
     */
    private fun logSecurityEvent() {
        try {
            val timestamp = System.currentTimeMillis()
            val logMessage = "EMERGENCY FACTORY RESET - Wake phrase detected twice - Device compromised"
            
            // Log to preferences (will be erased by factory reset)
            preferencesManager.logPhraseDetection(logMessage, timestamp)
            
            // Log to system
            Log.w(TAG, "SECURITY EVENT LOGGED: $logMessage")
            
            // Additional logging could include:
            // - Send final security notification to emergency contact
            // - Log to external security service if configured
            // - Write to system security log if available
            
        } catch (e: Exception) {
            Log.e(TAG, "Error logging security event", e)
        }
    }
    
    /**
     * Perform final cleanup before factory reset
     */
    private fun performFinalCleanup() {
        try {
            Log.i(TAG, "Performing final cleanup before factory reset")
            
            // Clear sensitive preferences
            preferencesManager.clearSensitiveData()
            
            // Additional cleanup could include:
            // - Secure deletion of specific files
            // - Clearing app caches
            // - Revoking permissions where possible
            // - Clearing encryption keys
            
            Log.i(TAG, "Final cleanup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during final cleanup", e)
        }
    }
    
    /**
     * Execute the actual factory reset
     */
    private fun executeFactoryReset() {
        try {
            Log.w(TAG, "EXECUTING FACTORY RESET - DEVICE WILL BE WIPED")
            
            // Method 1: Try Device Admin API (requires device admin permissions)
            if (attemptDeviceAdminReset()) {
                Log.w(TAG, "Factory reset initiated via Device Admin")
                return
            }
            
            // Method 2: Try system recovery reset (requires root or system permissions)
            if (attemptRecoveryReset()) {
                Log.w(TAG, "Factory reset initiated via recovery")
                return
            }
            
            // Method 3: Use Android system settings intent (requires user interaction)
            if (attemptSettingsReset()) {
                Log.w(TAG, "Factory reset initiated via settings intent")
                return
            }
            
            // If all methods fail
            Log.e(TAG, "CRITICAL: All factory reset methods failed - device remains unsecured")
            
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR during factory reset execution", e)
        }
    }
    
    /**
     * Attempt factory reset via Device Admin API
     */
    private fun attemptDeviceAdminReset(): Boolean {
        return try {
            // Use our DeviceAdminReceiver helper to perform the reset
            com.wype.security.admin.WypeDeviceAdminReceiver.performFactoryReset(this)
        } catch (e: Exception) {
            Log.e(TAG, "Device Admin factory reset failed", e)
            false
        }
    }
    
    /**
     * Attempt factory reset via recovery mode
     */
    private fun attemptRecoveryReset(): Boolean {
        return try {
            // This requires root permissions or system-level access
            // Implementation would execute recovery commands
            
            Log.w(TAG, "Recovery mode factory reset not implemented - requires root permissions")
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "Recovery factory reset failed", e)
            false
        }
    }
    
    /**
     * Attempt factory reset via system settings intent
     */
    private fun attemptSettingsReset(): Boolean {
        return try {
            // Launch system factory reset settings
            val resetIntent = Intent("android.settings.MASTER_CLEAR").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            
            startActivity(resetIntent)
            
            Log.w(TAG, "System settings factory reset intent launched")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Settings factory reset failed", e)
            false
        }
    }
}
