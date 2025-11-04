package com.wype.security.hotword

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Boot persistence receiver for the Wype protection system
 * 
 * Silently restarts the hotword service if protection mode was armed before reboot.
 * Provides clean, null-safe boot handling with no UI popups or user notifications.
 * 
 * Responds to:
 * - ACTION_BOOT_COMPLETED: Standard boot completion
 * - ACTION_LOCKED_BOOT_COMPLETED: Early boot completion (before user unlock)
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
        
        // Actions this receiver handles
        private const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
        private const val ACTION_LOCKED_BOOT_COMPLETED = "android.intent.action.LOCKED_BOOT_COMPLETED"
    }
    
    // Coroutine scope for async operations during boot
    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onReceive(context: Context?, intent: Intent?) {
        // Null safety checks
        if (context == null || intent == null) {
            Log.w(TAG, "Received null context or intent, skipping boot handling")
            return
        }
        
        val action = intent.action
        if (action == null) {
            Log.w(TAG, "Received intent with null action, skipping")
            return
        }
        
        Log.d(TAG, "Boot receiver triggered with action: $action")
        
        when (action) {
            ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "Device boot completed, checking protection mode state")
                handleBootCompleted(context)
            }
            ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.i(TAG, "Device locked boot completed, checking protection mode state")
                handleLockedBootCompleted(context)
            }
            else -> {
                Log.w(TAG, "Received unexpected action: $action")
            }
        }
    }
    
    /**
     * Handle standard boot completion
     * Full device boot with user unlocked - safe to access all DataStore
     */
    private fun handleBootCompleted(context: Context) {
        receiverScope.launch {
            try {
                val wasArmed = ProtectionMode.isArmedSuspend(context)
                Log.d(TAG, "Protection mode state before reboot: $wasArmed")
                
                if (wasArmed) {
                    Log.i(TAG, "Protection was armed before reboot - restarting hotword service")
                    startHotwordServiceSilently(context)
                } else {
                    Log.d(TAG, "Protection was not armed before reboot - no action needed")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking protection mode state during boot", e)
                // Don't restart service if we can't determine previous state
            }
        }
    }
    
    /**
     * Handle locked boot completion
     * Early boot stage - device may still be encrypted, user not unlocked
     * More cautious approach to avoid issues with DataStore access
     */
    private fun handleLockedBootCompleted(context: Context) {
        receiverScope.launch {
            try {
                // Use application context to avoid issues during early boot
                val appContext = context.applicationContext
                
                val wasArmed = ProtectionMode.isArmedSuspend(appContext)
                Log.d(TAG, "Protection mode state at locked boot: $wasArmed")
                
                if (wasArmed) {
                    Log.i(TAG, "Protection was armed - scheduling service start for after unlock")
                    // For locked boot, we still try to start the service
                    // The service itself will handle any encryption/unlock requirements
                    startHotwordServiceSilently(appContext)
                } else {
                    Log.d(TAG, "Protection was not armed - no service start needed")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during locked boot handling", e)
                // Silently fail - don't show any errors to user during boot
            }
        }
    }
    
    /**
     * Silently start the hotword service with no user notifications
     * Uses the same service start method as manual activation
     */
    private fun startHotwordServiceSilently(context: Context) {
        try {
            val serviceIntent = Intent(context, WypeHotwordService::class.java).apply {
                action = WypeHotwordService.ACTION_START_HOTWORD_DETECTION
                // Add flag to indicate this is a boot restart
                putExtra("boot_restart", true)
            }
            
            // Start as foreground service (required for background service start)
            context.startForegroundService(serviceIntent)
            
            Log.i(TAG, "✅ Hotword service start requested after boot (silent)")
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting service after boot", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Illegal state starting service after boot", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting service after boot", e)
        }
    }
    
    /**
     * Check if this receiver should handle boot events
     * Useful for debugging or feature flags
     */
    private fun shouldHandleBoot(context: Context): Boolean {
        return try {
            // Could add feature flags or user preferences here
            // For now, always handle boot if protection was armed
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error checking boot handling preference", e)
            true // Default to handling boot
        }
    }
}

/*
 * ================================
 * BOOT RECEIVER IMPLEMENTATION NOTES
 * ================================
 * 
 * 1. Null Safety:
 *    - All parameters checked for null before use
 *    - Graceful degradation if context/intent is null
 *    - No exceptions thrown that could crash boot process
 * 
 * 2. Silent Operation:
 *    - No user notifications or popups
 *    - All logging is debug/info level, not user-visible
 *    - Service started silently in background
 * 
 * 3. Boot Actions Handled:
 *    - BOOT_COMPLETED: Standard boot after user unlock
 *    - LOCKED_BOOT_COMPLETED: Early boot before user unlock
 *    - Both use same logic but locked boot is more cautious
 * 
 * 4. DataStore Integration:
 *    - Uses ProtectionMode.isArmedSuspend() for async DataStore access
 *    - Proper coroutine scope for receiver context
 *    - Exception handling prevents boot process interference
 * 
 * 5. Service Starting:
 *    - Uses startForegroundService() as required for background starts
 *    - Includes boot_restart flag for service to handle differently if needed
 *    - Same ACTION_START_HOTWORD_DETECTION as manual starts
 * 
 * 6. Error Handling:
 *    - Try-catch around all major operations
 *    - Logging for debugging but no user-facing errors
 *    - Graceful failure that doesn't break boot process
 * 
 * Expected behavior:
 * 1. Device reboots
 * 2. BootReceiver.onReceive() called with BOOT_COMPLETED
 * 3. Receiver checks ProtectionMode.isArmedSuspend()
 * 4. If true: starts WypeHotwordService silently
 * 5. If false: does nothing
 * 6. Service starts with KWS engine, ready for emergency phrases
 * 7. No user notification or UI - completely silent restart
 */
