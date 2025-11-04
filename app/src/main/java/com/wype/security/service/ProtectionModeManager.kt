package com.wype.security.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.wype.security.utils.PreferencesManager
import kotlinx.coroutines.*

/**
 * Protection Mode Manager
 * 
 * Centrally manages the protection mode system including:
 * - Entering/exiting protection mode
 * - Coordinating foreground service lifecycle
 * - Managing wake word detection
 * - Triggering emergency actions (SMS + Factory Reset)
 * - Silent operation with invisible notifications
 */
class ProtectionModeManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ProtectionModeManager"
        
        @Volatile
        private var instance: ProtectionModeManager? = null
        
        fun getInstance(context: Context): ProtectionModeManager {
            return instance ?: synchronized(this) {
                instance ?: ProtectionModeManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val preferencesManager = PreferencesManager(context)
    private var isProtectionModeActive = false
    private var protectionServiceIntent: Intent? = null
    
    /**
     * Enter protection mode automatically after phrase recording
     */
    fun enterProtectionMode(recordedPhrase: String) {
        if (isProtectionModeActive) {
            Log.w(TAG, "Protection mode already active")
            return
        }
        
        try {
            Log.i(TAG, "ENTERING PROTECTION MODE - Recording phrase and starting silent protection")
            
            // Store the recorded phrase securely
            preferencesManager.setWakePhrase(recordedPhrase)
            preferencesManager.setProtectionModeEnabled(true)
            preferencesManager.setProtectionModeStartTime(System.currentTimeMillis())
            
            // Start the protection foreground service
            startProtectionService()
            
            // Mark protection mode as active
            isProtectionModeActive = true
            
            Log.w(TAG, "PROTECTION MODE ACTIVATED - Device is now protected with phrase: '$recordedPhrase'")
            Log.i(TAG, "Protection running silently - say phrase twice to trigger emergency actions")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error entering protection mode", e)
        }
    }
    
    /**
     * Exit protection mode (for testing/debugging purposes)
     */
    fun exitProtectionMode() {
        if (!isProtectionModeActive) {
            Log.d(TAG, "Protection mode not active")
            return
        }
        
        try {
            Log.i(TAG, "EXITING PROTECTION MODE")
            
            // Stop protection service
            stopProtectionService()
            
            // Update preferences
            preferencesManager.setProtectionModeEnabled(false)
            
            // Mark as inactive
            isProtectionModeActive = false
            
            Log.i(TAG, "Protection mode deactivated")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error exiting protection mode", e)
        }
    }
    
    /**
     * Start the protection foreground service
     */
    private fun startProtectionService() {
        try {
            protectionServiceIntent = Intent(context, ProtectionModeService::class.java).apply {
                action = ProtectionModeService.ACTION_START_PROTECTION
            }
            
            // Start as foreground service for continuous operation
            context.startForegroundService(protectionServiceIntent)
            
            Log.i(TAG, "Protection foreground service started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting protection service", e)
        }
    }
    
    /**
     * Stop the protection foreground service
     */
    private fun stopProtectionService() {
        try {
            protectionServiceIntent?.let { intent ->
                intent.action = ProtectionModeService.ACTION_STOP_PROTECTION
                context.startService(intent)
            }
            
            Log.i(TAG, "Protection service stop requested")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping protection service", e)
        }
    }
    
    /**
     * Trigger emergency actions: SMS + Factory Reset
     */
    fun triggerEmergencyActions(allowWithoutProtectionMode: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.w(TAG, "EMERGENCY ACTIONS TRIGGERED - Executing SMS + Factory Reset (override=$allowWithoutProtectionMode)")
                
                // 1. Send Emergency SMS first (faster)
                sendEmergencySMS()
                
                // 2. Brief delay to ensure SMS is sent
                delay(2000)
                
                // 3. Trigger Factory Reset
                triggerFactoryReset(allowWithoutProtectionMode)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during emergency actions", e)
            }
        }
    }
    
    /**
     * Send emergency SMS using foreground service for reliability
     */
    private fun sendEmergencySMS() {
        try {
            Log.w(TAG, "🚨 EMERGENCY SMS - Sending location + alert message...")
            
            val emergencyIntent = Intent(context, EmergencySmsService::class.java).apply {
                action = EmergencySmsService.ACTION_SEND_EMERGENCY_SMS
                // Add emergency context
                putExtra("emergency_type", "wake_word_double_detection")
                putExtra("trigger_time", System.currentTimeMillis())
            }
            
            // Use foreground service for critical emergency SMS
            context.startForegroundService(emergencyIntent)
            
            Log.w(TAG, "✅ Emergency SMS foreground service started")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL: Error starting emergency SMS service", e)
            // Don't let SMS failure stop factory reset
        }
    }
    
    /**
     * Trigger factory reset using foreground service
     */
    private fun triggerFactoryReset(allowWithoutProtectionMode: Boolean = false) {
        try {
            Log.w(TAG, "🗑️ EMERGENCY FACTORY RESET - Device security compromised - wiping device...")
            
            val factoryResetIntent = Intent(context, FactoryResetService::class.java).apply {
                action = FactoryResetService.ACTION_FACTORY_RESET
                // Add emergency context
                putExtra("emergency_trigger", "double_wake_word_detection")
                putExtra("trigger_time", System.currentTimeMillis())
                putExtra("protection_duration", System.currentTimeMillis() - preferencesManager.getProtectionModeStartTime())
                putExtra("allow_without_protection_mode", allowWithoutProtectionMode)
            }
            
            // Use foreground service for critical factory reset
            context.startForegroundService(factoryResetIntent)
            
            Log.w(TAG, "⚠️ FACTORY RESET INITIATED - Device will be wiped (override=$allowWithoutProtectionMode)")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL: Error starting factory reset service", e)
        }
    }
    
    /**
     * Check if protection mode is currently active
     */
    fun isProtectionModeActive(): Boolean {
        return isProtectionModeActive || preferencesManager.isProtectionModeEnabled()
    }
    
    /**
     * Get protection mode status information
     */
    fun getProtectionModeStatus(): Map<String, Any> {
        val startTime = preferencesManager.getProtectionModeStartTime()
        val currentTime = System.currentTimeMillis()
        val protectionDurationMs = if (startTime > 0) currentTime - startTime else 0
        val protectionHours = protectionDurationMs / (1000 * 60 * 60)
        
        return mapOf(
            "isActive" to isProtectionModeActive(),
            "recordedPhrase" to (preferencesManager.getWakePhrase() ?: "None"),
            "startTime" to startTime,
            "protectionDurationHours" to protectionHours,
            "hasEmergencyContact" to (!preferencesManager.getBuddyPhone().isNullOrEmpty())
        )
    }
    
    /**
     * Initialize protection mode on app startup if it was previously active
     */
    fun initializeOnStartup() {
        if (preferencesManager.isProtectionModeEnabled()) {
            val recordedPhrase = preferencesManager.getWakePhrase()
            if (!recordedPhrase.isNullOrEmpty()) {
                Log.i(TAG, "Restoring protection mode from previous session")
                isProtectionModeActive = true
                startProtectionService()
            }
        }
    }
}
