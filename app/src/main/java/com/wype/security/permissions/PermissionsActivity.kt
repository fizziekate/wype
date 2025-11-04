package com.wype.security.permissions

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.wype.security.hotword.WypeHotwordService
import com.wype.security.hotword.ProtectionMode
import kotlinx.coroutines.launch

/**
 * Sample Activity demonstrating proper permission workflow for Wype hotword service
 * 
 * Shows best practices for:
 * - Checking current permission status
 * - Requesting permissions step-by-step with user-friendly flow
 * - Handling permission results appropriately
 * - Starting hotword service only after required permissions are granted
 * - Modern Android 13/14+ compatibility
 */
class PermissionsActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "PermissionsActivity"
    }
    
    private lateinit var permissionsManager: PermissionsHelper.PermissionsManager
    private var isHotwordServiceStartPending = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.i(TAG, "Starting permission flow for Wype hotword service")
        
        // Initialize permissions manager with result callback
        permissionsManager = PermissionsHelper.create(this) { result ->
            handlePermissionResult(result)
        }
        
        // Check current permission status and decide next action
        checkPermissionsAndProceed()
    }
    
    /**
     * Check current permissions and proceed accordingly
     */
    private fun checkPermissionsAndProceed() {
        val status = permissionsManager.getStatus()
        
        Log.i(TAG, "Current permission status:")
        Log.i(TAG, "- Core (RECORD_AUDIO): ${status.corePermissions}")
        Log.i(TAG, "- Notifications: ${status.notificationPermission}")
        Log.i(TAG, "- Emergency (Location/SMS): ${status.emergencyPermissions}")
        Log.i(TAG, "- Battery optimization disabled: ${status.batteryOptimizationDisabled}")
        
        when {
            status.canStartHotwordService() -> {
                Log.i(TAG, "✅ Essential permissions granted - can start hotword service")
                
                if (status.isOptimalConfiguration()) {
                    Log.i(TAG, "🚀 Optimal configuration detected - starting service immediately")
                    startHotwordService()
                } else {
                    showOptimalConfigurationDialog(status)
                }
            }
            
            status.corePermissions && !status.notificationPermission -> {
                Log.w(TAG, "⚠️ Core permissions granted but notifications missing")
                requestNotificationPermissionWithRationale()
            }
            
            else -> {
                Log.w(TAG, "❌ Missing essential permissions - starting permission flow")
                showPermissionIntroDialog()
            }
        }
    }
    
    /**
     * Show introduction dialog explaining why permissions are needed
     */
    private fun showPermissionIntroDialog() {
        AlertDialog.Builder(this)
            .setTitle("Wype Protection Setup")
            .setMessage("""
                Wype needs certain permissions to protect you:
                
                🎤 Microphone: To detect emergency keywords
                🔔 Notifications: To show service status (Android 13+)
                📍 Location: For emergency GPS coordinates
                📱 SMS: To send emergency alerts
                🔋 Battery: To run reliably in background
                
                Let's set these up step by step.
            """.trimIndent())
            .setPositiveButton("Get Started") { _, _ ->
                startStepByStepPermissionFlow()
            }
            .setNegativeButton("Cancel") { _, _ ->
                showCancellationMessage()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Start step-by-step permission request flow
     */
    private fun startStepByStepPermissionFlow() {
        Log.i(TAG, "Starting step-by-step permission flow")
        isHotwordServiceStartPending = true
        
        PermissionStrategy.requestPermissionsStepByStep(permissionsManager) { success ->
            Log.i(TAG, "Step-by-step flow completed: $success")
            
            // Check final status after all permission requests
            val finalStatus = permissionsManager.getStatus()
            if (finalStatus.canStartHotwordService()) {
                startHotwordService()
            } else {
                showInsufficientPermissionsDialog()
            }
        }
    }
    
    /**
     * Request notification permission with rationale
     */
    private fun requestNotificationPermissionWithRationale() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission Needed")
            .setMessage("""
                Wype needs notification permission to:
                • Show when protection is active
                • Display service status
                • Alert you to any issues
                
                This helps ensure the service runs reliably.
            """.trimIndent())
            .setPositiveButton("Grant Permission") { _, _ ->
                permissionsManager.requestNotificationPermission()
            }
            .setNegativeButton("Skip") { _, _ ->
                // Can still start service without notifications on older Android
                startHotwordService()
            }
            .show()
    }
    
    /**
     * Show dialog for optimal configuration options
     */
    private fun showOptimalConfigurationDialog(status: PermissionsHelper.PermissionStatus) {
        val missingFeatures = mutableListOf<String>()
        
        if (!status.emergencyPermissions) {
            missingFeatures.add("Emergency alerts (Location/SMS)")
        }
        if (!status.batteryOptimizationDisabled) {
            missingFeatures.add("Battery optimization exemption")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Ready to Start!")
            .setMessage("""
                Wype hotword service is ready to start.
                
                Optional enhancements available:
                ${missingFeatures.joinToString("\n• ", "• ")}
                
                You can add these later in Settings.
            """.trimIndent())
            .setPositiveButton("Start Service") { _, _ ->
                startHotwordService()
            }
            .setNeutralButton("Optimize First") { _, _ ->
                requestMissingOptimalFeatures(status)
            }
            .setNegativeButton("Cancel") { _, _ ->
                showCancellationMessage()
            }
            .show()
    }
    
    /**
     * Request missing features for optimal configuration
     */
    private fun requestMissingOptimalFeatures(status: PermissionsHelper.PermissionStatus) {
        if (!status.emergencyPermissions) {
            Log.i(TAG, "Requesting emergency permissions")
            permissionsManager.requestEmergencyPermissions()
        } else if (!status.batteryOptimizationDisabled) {
            Log.i(TAG, "Requesting battery optimization exemption")
            permissionsManager.requestBatteryOptimizationExemption()
        } else {
            // All optimal features are already enabled
            startHotwordService()
        }
    }
    
    /**
     * Handle permission request results
     */
    private fun handlePermissionResult(result: PermissionsHelper.PermissionResult) {
        Log.i(TAG, "Permission result received:")
        Log.i(TAG, "- Granted: ${result.grantedPermissions}")
        Log.i(TAG, "- Denied: ${result.deniedPermissions}")
        Log.i(TAG, "- Permanent: ${result.permanentlyDeniedPermissions}")
        
        when {
            result.isCoreGranted() && result.isNotificationGranted() -> {
                Log.i(TAG, "✅ Essential permissions granted")
                if (isHotwordServiceStartPending) {
                    startHotwordService()
                    isHotwordServiceStartPending = false
                } else {
                    // Continue with current flow (may be requesting additional permissions)
                    showToast("Permissions granted successfully")
                }
            }
            
            result.deniedPermissions.isNotEmpty() -> {
                Log.w(TAG, "⚠️ Some permissions were denied")
                handleDeniedPermissions(result.deniedPermissions)
            }
            
            result.permanentlyDeniedPermissions.isNotEmpty() -> {
                Log.e(TAG, "❌ Some permissions were permanently denied")
                showPermanentlyDeniedDialog(result.permanentlyDeniedPermissions)
            }
            
            else -> {
                Log.d(TAG, "Permission result processed, checking current status")
                val currentStatus = permissionsManager.getStatus()
                if (currentStatus.canStartHotwordService() && isHotwordServiceStartPending) {
                    startHotwordService()
                    isHotwordServiceStartPending = false
                }
            }
        }
    }
    
    /**
     * Handle denied permissions with appropriate user guidance
     */
    private fun handleDeniedPermissions(deniedPermissions: List<String>) {
        val criticalPermissions = deniedPermissions.filter { permission ->
            permission in PermissionsHelper.CORE_PERMISSIONS || 
            permission == android.Manifest.permission.POST_NOTIFICATIONS
        }
        
        if (criticalPermissions.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Important Permissions Needed")
                .setMessage("""
                    Some critical permissions were denied:
                    ${criticalPermissions.joinToString("\n• ", "• ")}
                    
                    Wype needs these to function properly. Would you like to try again?
                """.trimIndent())
                .setPositiveButton("Try Again") { _, _ ->
                    // Request only the denied critical permissions
                    requestCriticalPermissions(criticalPermissions)
                }
                .setNegativeButton("Continue Anyway") { _, _ ->
                    val status = permissionsManager.getStatus()
                    if (status.canStartHotwordService()) {
                        startHotwordService()
                    } else {
                        showInsufficientPermissionsDialog()
                    }
                }
                .show()
        } else {
            showToast("Optional permissions denied - service will work with reduced functionality")
        }
    }
    
    /**
     * Show dialog for permanently denied permissions
     */
    private fun showPermanentlyDeniedDialog(permanentlyDeniedPermissions: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("Permission Settings Required")
            .setMessage("""
                Some permissions were permanently denied:
                ${permanentlyDeniedPermissions.joinToString("\n• ", "• ")}
                
                Please enable these manually in Settings for full functionality.
            """.trimIndent())
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Continue") { _, _ ->
                val status = permissionsManager.getStatus()
                if (status.canStartHotwordService()) {
                    startHotwordService()
                } else {
                    showInsufficientPermissionsDialog()
                }
            }
            .show()
    }
    
    /**
     * Request critical permissions that were denied
     */
    private fun requestCriticalPermissions(permissions: List<String>) {
        // For simplicity, request core permissions if RECORD_AUDIO was denied
        if (permissions.contains(android.Manifest.permission.RECORD_AUDIO)) {
            permissionsManager.requestCorePermissions()
        } else if (permissions.contains(android.Manifest.permission.POST_NOTIFICATIONS)) {
            permissionsManager.requestNotificationPermission()
        }
    }
    
    /**
     * Show insufficient permissions dialog
     */
    private fun showInsufficientPermissionsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cannot Start Service")
            .setMessage("""
                Wype needs microphone permission to detect emergency keywords.
                
                Without this permission, the service cannot function.
            """.trimIndent())
            .setPositiveButton("Try Again") { _, _ ->
                checkPermissionsAndProceed()
            }
            .setNegativeButton("Exit") { _, _ ->
                finish()
            }
            .show()
    }
    
    /**
     * Show cancellation message and finish
     */
    private fun showCancellationMessage() {
        showToast("Setup cancelled - Wype protection not activated")
        finish()
    }
    
    /**
     * Start the hotword service after permissions are granted
     */
    private fun startHotwordService() {
        Log.i(TAG, "🚀 Starting Wype hotword service")
        
        lifecycleScope.launch {
            try {
                // Ensure protection mode is set (could be armed or disarmed based on user preference)
                val currentlyArmed = ProtectionMode.isArmedSuspend(this@PermissionsActivity)
                Log.i(TAG, "Protection mode currently: ${if (currentlyArmed) "ARMED" else "DISARMED"}")
                
                // Start the hotword service
                val serviceIntent = Intent(this@PermissionsActivity, WypeHotwordService::class.java).apply {
                    action = WypeHotwordService.ACTION_START_HOTWORD_DETECTION
                }
                
                startForegroundService(serviceIntent)
                
                showToast("✅ Wype protection service started")
                Log.i(TAG, "✅ Hotword service started successfully")
                
                // Finish this activity and return to main app
                finish()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting hotword service", e)
                showToast("❌ Failed to start service: ${e.message}")
            }
        }
    }
    
    /**
     * Open app settings for manual permission management
     */
    private fun openAppSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app settings", e)
            showToast("Unable to open settings")
        }
    }
    
    /**
     * Utility method to show toast messages
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

/*
 * ================================
 * INTEGRATION EXAMPLES
 * ================================
 * 
 * 1. Launch from MainActivity:
 * ```kotlin
 * class MainActivity : AppCompatActivity() {
 *     
 *     fun startWypeProtection() {
 *         // Check if permissions are already granted
 *         if (PermissionsHelper.hasCorePermissions(this) && 
 *             PermissionsHelper.hasNotificationPermission(this)) {
 *             // Start service directly
 *             startHotwordService()
 *         } else {
 *             // Launch permissions flow
 *             val intent = Intent(this, PermissionsActivity::class.java)
 *             startActivity(intent)
 *         }
 *     }
 * }
 * ```
 * 
 * 2. Simple permission check before service start:
 * ```kotlin
 * fun startServiceWithPermissionCheck() {
 *     val status = PermissionsHelper.getPermissionStatus(this)
 *     
 *     when {
 *         status.canStartHotwordService() -> {
 *             startHotwordService()
 *         }
 *         else -> {
 *             // Launch full permission flow
 *             startActivity(Intent(this, PermissionsActivity::class.java))
 *         }
 *     }
 * }
 * ```
 * 
 * 3. Add to AndroidManifest.xml:
 * ```xml
 * <activity
 *     android:name="com.wype.security.permissions.PermissionsActivity"
 *     android:exported="false"
 *     android:theme="@style/Theme.Wype.NoActionBar"
 *     android:screenOrientation="portrait" />
 * ```
 * 
 * 4. Expected user flow:
 * - User taps "Start Protection" in main app
 * - App checks current permissions
 * - If missing: Launch PermissionsActivity
 * - User sees explanation dialog
 * - Step-by-step permission requests
 * - Final confirmation and service start
 * - Return to main app with service running
 * 
 * This provides a smooth, educational permission flow that maximizes
 * user understanding and permission grant rates while maintaining
 * compliance with modern Android permission best practices.
 */
