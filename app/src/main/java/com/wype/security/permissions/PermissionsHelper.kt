package com.wype.security.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Modern Android permissions helper for Wype security features
 * 
 * Handles runtime permissions correctly for Android 13/14+ including:
 * - RECORD_AUDIO (required for hotword detection)
 * - POST_NOTIFICATIONS (Android 13+)
 * - ACCESS_FINE_LOCATION (for emergency GPS)
 * - SEND_SMS (for emergency SMS alerts)
 * - Battery optimization exemption
 * 
 * Uses modern ActivityResultContracts API, no deprecated methods.
 */
class PermissionsHelper private constructor() {
    
    companion object {
        const val TAG = "PermissionsHelper"
        
        // Core permissions required for basic functionality
        val CORE_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO
        )
        
        // Notification permission (Android 13+)
        val NOTIFICATION_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
        
        // Emergency feature permissions (optional but recommended)
        val EMERGENCY_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS
        )
        
        // All permissions combined
        private val ALL_PERMISSIONS = CORE_PERMISSIONS + NOTIFICATION_PERMISSIONS + EMERGENCY_PERMISSIONS
        
        /**
         * Create PermissionsHelper for ComponentActivity
         * Registers permission launchers automatically
         */
        fun create(
            activity: ComponentActivity,
            onPermissionsResult: (PermissionResult) -> Unit
        ): PermissionsManager {
            return PermissionsManager(activity, onPermissionsResult)
        }
        
        /**
         * Create PermissionsHelper for Fragment
         * Registers permission launchers automatically
         */
        fun create(
            fragment: Fragment,
            onPermissionsResult: (PermissionResult) -> Unit
        ): PermissionsManager {
            return PermissionsManager(fragment, onPermissionsResult)
        }
        
        /**
         * Check if all core permissions are granted
         */
        fun hasCorePermissions(context: Context): Boolean {
            return CORE_PERMISSIONS.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        }
        
        /**
         * Check if notification permission is granted (Android 13+)
         */
        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Not required on older versions
            }
        }
        
        /**
         * Check if emergency permissions are granted
         */
        fun hasEmergencyPermissions(context: Context): Boolean {
            return EMERGENCY_PERMISSIONS.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        }
        
        /**
         * Check if app is exempt from battery optimizations
         */
        fun isBatteryOptimizationDisabled(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true // Not applicable on older versions
            }
        }
        
        /**
         * Get detailed permission status
         */
        fun getPermissionStatus(context: Context): PermissionStatus {
            return PermissionStatus(
                corePermissions = hasCorePermissions(context),
                notificationPermission = hasNotificationPermission(context),
                emergencyPermissions = hasEmergencyPermissions(context),
                batteryOptimizationDisabled = isBatteryOptimizationDisabled(context),
                allPermissionsGranted = hasCorePermissions(context) && 
                                      hasNotificationPermission(context) && 
                                      hasEmergencyPermissions(context)
            )
        }
    }
    
    /**
     * Permission result data class
     */
    data class PermissionResult(
        val grantedPermissions: List<String>,
        val deniedPermissions: List<String>,
        val permanentlyDeniedPermissions: List<String>,
        val allGranted: Boolean
    ) {
        fun hasPermission(permission: String): Boolean = grantedPermissions.contains(permission)
        fun isCoreGranted(): Boolean = CORE_PERMISSIONS.all { hasPermission(it) }
        fun isNotificationGranted(): Boolean = NOTIFICATION_PERMISSIONS.isEmpty() || NOTIFICATION_PERMISSIONS.all { hasPermission(it) }
        fun isEmergencyGranted(): Boolean = EMERGENCY_PERMISSIONS.all { hasPermission(it) }
    }
    
    /**
     * Overall permission status
     */
    data class PermissionStatus(
        val corePermissions: Boolean,
        val notificationPermission: Boolean,
        val emergencyPermissions: Boolean,
        val batteryOptimizationDisabled: Boolean,
        val allPermissionsGranted: Boolean
    ) {
        fun canStartHotwordService(): Boolean = corePermissions && notificationPermission
        fun canSendEmergencyAlerts(): Boolean = emergencyPermissions
        fun isOptimalConfiguration(): Boolean = allPermissionsGranted && batteryOptimizationDisabled
    }
    
    /**
     * Main permissions manager class
     */
    class PermissionsManager {
        
        private val context: Context
        private val onPermissionsResult: (PermissionResult) -> Unit
        private var multiplePermissionsLauncher: ActivityResultLauncher<Array<String>>? = null
        private var batteryOptimizationLauncher: ActivityResultLauncher<Intent>? = null
        
        // Constructor for ComponentActivity
        constructor(activity: ComponentActivity, onPermissionsResult: (PermissionResult) -> Unit) {
            this.context = activity
            this.onPermissionsResult = onPermissionsResult
            
            // Register permission launchers
            multiplePermissionsLauncher = activity.registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                handlePermissionResult(permissions)
            }
            
            batteryOptimizationLauncher = activity.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                Log.d(TAG, "Battery optimization result: ${result.resultCode}")
                // Check if battery optimization is now disabled
                val isDisabled = isBatteryOptimizationDisabled(context)
                Log.i(TAG, "Battery optimization disabled: $isDisabled")
            }
        }
        
        // Constructor for Fragment
        constructor(fragment: Fragment, onPermissionsResult: (PermissionResult) -> Unit) {
            this.context = fragment.requireContext()
            this.onPermissionsResult = onPermissionsResult
            
            // Register permission launchers
            multiplePermissionsLauncher = fragment.registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                handlePermissionResult(permissions)
            }
            
            batteryOptimizationLauncher = fragment.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                Log.d(TAG, "Battery optimization result: ${result.resultCode}")
                val isDisabled = isBatteryOptimizationDisabled(context)
                Log.i(TAG, "Battery optimization disabled: $isDisabled")
            }
        }
        
        /**
         * Request core permissions (RECORD_AUDIO)
         */
        fun requestCorePermissions() {
            Log.i(TAG, "Requesting core permissions: ${CORE_PERMISSIONS.joinToString()}")
            multiplePermissionsLauncher?.launch(CORE_PERMISSIONS)
        }
        
        /**
         * Request notification permission (Android 13+)
         */
        fun requestNotificationPermission() {
            if (NOTIFICATION_PERMISSIONS.isNotEmpty()) {
                Log.i(TAG, "Requesting notification permission")
                multiplePermissionsLauncher?.launch(NOTIFICATION_PERMISSIONS)
            } else {
                Log.d(TAG, "Notification permission not required on this Android version")
                // Simulate success for older versions
                onPermissionsResult(PermissionResult(
                    grantedPermissions = emptyList(),
                    deniedPermissions = emptyList(), 
                    permanentlyDeniedPermissions = emptyList(),
                    allGranted = true
                ))
            }
        }
        
        /**
         * Request emergency permissions (LOCATION, SMS)
         */
        fun requestEmergencyPermissions() {
            Log.i(TAG, "Requesting emergency permissions: ${EMERGENCY_PERMISSIONS.joinToString()}")
            multiplePermissionsLauncher?.launch(EMERGENCY_PERMISSIONS)
        }
        
        /**
         * Request all permissions at once
         */
        fun requestAllPermissions() {
            Log.i(TAG, "Requesting all permissions: ${ALL_PERMISSIONS.joinToString()}")
            multiplePermissionsLauncher?.launch(ALL_PERMISSIONS)
        }
        
        /**
         * Request battery optimization exemption
         */
        fun requestBatteryOptimizationExemption() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (isBatteryOptimizationDisabled(context)) {
                    Log.d(TAG, "Battery optimization already disabled")
                    return
                }
                
                Log.i(TAG, "Requesting battery optimization exemption")
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    batteryOptimizationLauncher?.launch(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching battery optimization settings", e)
                    // Fallback to general battery optimization settings
                    try {
                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        batteryOptimizationLauncher?.launch(fallbackIntent)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Error launching fallback battery settings", e2)
                    }
                }
            } else {
                Log.d(TAG, "Battery optimization not applicable on this Android version")
            }
        }
        
        /**
         * Check if we should show rationale for permission
         */
        fun shouldShowRationale(permission: String): Boolean {
            return when (context) {
                is Activity -> context.shouldShowRequestPermissionRationale(permission)
                else -> false
            }
        }
        
        /**
         * Get current permission status
         */
        fun getStatus(): PermissionStatus = getPermissionStatus(context)
        
        /**
         * Handle permission request result
         */
        private fun handlePermissionResult(permissions: Map<String, Boolean>) {
            val granted = mutableListOf<String>()
            val denied = mutableListOf<String>()
            val permanentlyDenied = mutableListOf<String>()
            
            permissions.forEach { (permission, isGranted) ->
                if (isGranted) {
                    granted.add(permission)
                    Log.d(TAG, "Permission granted: $permission")
                } else {
                    denied.add(permission)
                    Log.w(TAG, "Permission denied: $permission")
                    
                    // Check if permanently denied (no rationale and not granted)
                    if (!shouldShowRationale(permission)) {
                        permanentlyDenied.add(permission)
                        Log.w(TAG, "Permission permanently denied: $permission")
                    }
                }
            }
            
            val result = PermissionResult(
                grantedPermissions = granted,
                deniedPermissions = denied,
                permanentlyDeniedPermissions = permanentlyDenied,
                allGranted = denied.isEmpty()
            )
            
            Log.i(TAG, "Permission result - Granted: ${granted.size}, Denied: ${denied.size}, Permanent: ${permanentlyDenied.size}")
            onPermissionsResult(result)
        }
    }
}

/**
 * Permission request strategy for optimal user experience
 */
object PermissionStrategy {
    
    /**
     * Step-by-step permission request strategy
     * Requests permissions in logical order for best user experience
     */
    fun requestPermissionsStepByStep(
        permissionsManager: PermissionsHelper.PermissionsManager,
        onComplete: (Boolean) -> Unit
    ) {
        var step = 0
        val totalSteps = 4
        
        fun nextStep(success: Boolean) {
            if (!success) {
                Log.w(PermissionsHelper.TAG, "Permission step $step failed, but continuing...")
            }
            
            step++
            when (step) {
                1 -> {
                    Log.i(PermissionsHelper.TAG, "Step 1/4: Requesting core permissions (RECORD_AUDIO)")
                    permissionsManager.requestCorePermissions()
                }
                2 -> {
                    Log.i(PermissionsHelper.TAG, "Step 2/4: Requesting notification permission")
                    permissionsManager.requestNotificationPermission()
                }
                3 -> {
                    Log.i(PermissionsHelper.TAG, "Step 3/4: Requesting emergency permissions")
                    permissionsManager.requestEmergencyPermissions()
                }
                4 -> {
                    Log.i(PermissionsHelper.TAG, "Step 4/4: Requesting battery optimization exemption")
                    permissionsManager.requestBatteryOptimizationExemption()
                    onComplete(true)
                }
                else -> onComplete(true)
            }
        }
        
        // Start the process
        nextStep(true)
    }
}

/*
 * ================================
 * USAGE EXAMPLES
 * ================================
 * 
 * Basic usage in Activity:
 * ```kotlin
 * class MainActivity : ComponentActivity() {
 *     private lateinit var permissionsManager: PermissionsHelper.PermissionsManager
 *     
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         
 *         // Create permissions manager
 *         permissionsManager = PermissionsHelper.create(this) { result ->
 *             handlePermissionResult(result)
 *         }
 *         
 *         // Check current status
 *         val status = permissionsManager.getStatus()
 *         if (status.canStartHotwordService()) {
 *             startHotwordService()
 *         } else {
 *             requestRequiredPermissions()
 *         }
 *     }
 *     
 *     private fun handlePermissionResult(result: PermissionsHelper.PermissionResult) {
 *         when {
 *             result.isCoreGranted() && result.isNotificationGranted() -> {
 *                 Log.i(TAG, "Essential permissions granted - can start service")
 *                 startHotwordService()
 *             }
 *             result.deniedPermissions.isNotEmpty() -> {
 *                 Log.w(TAG, "Some permissions denied: ${result.deniedPermissions}")
 *                 showPermissionRationale(result.deniedPermissions)
 *             }
 *             result.permanentlyDeniedPermissions.isNotEmpty() -> {
 *                 Log.e(TAG, "Permanently denied: ${result.permanentlyDeniedPermissions}")
 *                 showSettingsDialog()
 *             }
 *         }
 *     }
 * }
 * ```
 * 
 * Fragment usage:
 * ```kotlin
 * class SettingsFragment : Fragment() {
 *     private lateinit var permissionsManager: PermissionsHelper.PermissionsManager
 *     
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         
 *         permissionsManager = PermissionsHelper.create(this) { result ->
 *             updatePermissionUI(result)
 *         }
 *     }
 *     
 *     fun requestAllPermissions() {
 *         permissionsManager.requestAllPermissions()
 *     }
 * }
 * ```
 * 
 * Step-by-step permission flow:
 * ```kotlin
 * PermissionStrategy.requestPermissionsStepByStep(permissionsManager) { success ->
 *     if (success) {
 *         Log.i(TAG, "All permissions requested")
 *         startHotwordService()
 *     }
 * }
 * ```
 */
