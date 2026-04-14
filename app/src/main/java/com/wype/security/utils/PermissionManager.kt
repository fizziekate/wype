package com.wype.security.utils

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.wype.security.admin.WypeDeviceAdminReceiver

/**
 * Comprehensive permission manager for Wype security app
 */
class PermissionManager(private val activity: Activity) {
    
    companion object {
        private const val TAG = "PermissionManager"
        
        // Permission request codes
        const val PERMISSIONS_REQUEST_CODE = 1000
        const val DEVICE_ADMIN_REQUEST_CODE = 1001
        const val BATTERY_OPTIMIZATION_REQUEST_CODE = 1002
        const val OVERLAY_PERMISSION_REQUEST_CODE = 1003
        
        // Required permissions
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.GET_ACCOUNTS,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            Manifest.permission.SYSTEM_ALERT_WINDOW
        ).plus(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            } else emptyArray()
        ).plus(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                arrayOf(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
            } else emptyArray()
        )
    }
    
    private val devicePolicyManager: DevicePolicyManager = 
        activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponentName = ComponentName(activity, WypeDeviceAdminReceiver::class.java)
    
    /**
     * Check if all required permissions are granted
     */
    fun hasAllRequiredPermissions(): Boolean {
        return hasRuntimePermissions() && 
               isDeviceAdminEnabled() && 
               hasOverlayPermission() &&
               BatteryOptimizationHelper.isIgnoringBatteryOptimizations(activity)
    }
    
    /**
     * Check if runtime permissions are granted
     */
    private fun hasRuntimePermissions(): Boolean {
        return REQUIRED_PERMISSIONS.filter { permission ->
            // Filter out permissions that don't need runtime permission
            permission != Manifest.permission.WAKE_LOCK &&
            permission != Manifest.permission.FOREGROUND_SERVICE &&
            permission != Manifest.permission.RECEIVE_BOOT_COMPLETED &&
            permission != Manifest.permission.SYSTEM_ALERT_WINDOW &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || permission != Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
        }.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if device admin is enabled
     */
    fun isDeviceAdminEnabled(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponentName)
    }
    
    /**
     * Check if overlay permission is granted
     */
    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(activity)
        } else {
            true
        }
    }
    
    /**
     * Request all missing permissions with user-friendly explanations
     */
    fun requestAllPermissions() {
        Log.d(TAG, "Starting comprehensive permission request flow")
        
        // Step 1: Request runtime permissions
        if (!hasRuntimePermissions()) {
            requestRuntimePermissions()
            return
        }
        
        // Step 2: Request device admin
        if (!isDeviceAdminEnabled()) {
            requestDeviceAdminPermission()
            return
        }
        
        // Step 3: Request overlay permission
        if (!hasOverlayPermission()) {
            requestOverlayPermission()
            return
        }
        
        // Step 4: Request battery optimization exemption
        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(activity)) {
            requestBatteryOptimizationExemption()
            return
        }
        
        Log.d(TAG, "All permissions granted successfully")
    }
    
    /**
     * Request runtime permissions
     */
    private fun requestRuntimePermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter { permission ->
            // Filter out non-runtime permissions
            permission != Manifest.permission.WAKE_LOCK &&
            permission != Manifest.permission.FOREGROUND_SERVICE &&
            permission != Manifest.permission.RECEIVE_BOOT_COMPLETED &&
            permission != Manifest.permission.SYSTEM_ALERT_WINDOW &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || permission != Manifest.permission.FOREGROUND_SERVICE_MICROPHONE) &&
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (missingPermissions.isNotEmpty()) {
            Log.d(TAG, "Requesting runtime permissions: ${missingPermissions.contentToString()}")
            ActivityCompat.requestPermissions(activity, missingPermissions, PERMISSIONS_REQUEST_CODE)
        }
    }
    
    /**
     * Request device admin permission
     */
    fun requestDeviceAdminPermission() {
        Log.d(TAG, "Requesting device admin permission")
        
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Wype needs device admin permission to perform emergency factory reset when your security phrase is detected. This helps protect your data in emergency situations."
            )
        }
        
        try {
            activity.startActivityForResult(intent, DEVICE_ADMIN_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request device admin permission", e)
        }
    }

    /**
     * Open system Device Admin settings screen
     */
    fun openDeviceAdminSettings() {
        // Open Security settings where "Device admin apps" resides. ACTION_DEVICE_ADMIN_SETTINGS
        // isn't available on all SDKs/APIs.
        try {
            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Security settings", e)
            // Fallback to general settings
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS)
                activity.startActivity(fallback)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open general settings", e2)
            }
        }
    }
    
    /**
     * Request overlay permission
     */
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d(TAG, "Requesting overlay permission")
            
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            
            try {
                activity.startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request overlay permission", e)
                // Continue with next permission
                requestBatteryOptimizationExemption()
            }
        }
    }
    
    /**
     * Request battery optimization exemption
     */
    private fun requestBatteryOptimizationExemption() {
        Log.d(TAG, "Requesting battery optimization exemption")
        
        if (!BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(activity)) {
            // If direct request failed, try opening settings
            BatteryOptimizationHelper.openBatteryOptimizationSettings(activity)
        }
    }
    
    /**
     * Handle permission request results
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE -> {
                val allGranted = grantResults.isNotEmpty() && 
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                
                Log.d(TAG, "Runtime permissions result: allGranted=$allGranted")
                
                if (allGranted) {
                    // Continue with device admin request
                    if (!isDeviceAdminEnabled()) {
                        requestDeviceAdminPermission()
                    } else {
                        requestAllPermissions() // Continue with remaining permissions
                    }
                } else {
                    handlePermissionDenied(permissions, grantResults)
                }
                return true
            }
        }
        return false
    }
    
    /**
     * Handle activity results
     */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        when (requestCode) {
            DEVICE_ADMIN_REQUEST_CODE -> {
                val granted = resultCode == Activity.RESULT_OK
                Log.d(TAG, "Device admin result: granted=$granted")
                
                if (granted) {
                    // Continue with overlay permission
                    requestAllPermissions()
                } else {
                    Log.w(TAG, "Device admin permission denied")
                    // Still continue with other permissions
                    requestAllPermissions()
                }
                return true
            }
            
            OVERLAY_PERMISSION_REQUEST_CODE -> {
                val granted = hasOverlayPermission()
                Log.d(TAG, "Overlay permission result: granted=$granted")
                
                // Continue with battery optimization
                requestBatteryOptimizationExemption()
                return true
            }
            
            BATTERY_OPTIMIZATION_REQUEST_CODE -> {
                val granted = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(activity)
                Log.d(TAG, "Battery optimization result: granted=$granted")
                return true
            }
        }
        return false
    }
    
    /**
     * Handle permission denied cases
     */
    private fun handlePermissionDenied(permissions: Array<out String>, grantResults: IntArray) {
        permissions.forEachIndexed { index, permission ->
            if (grantResults[index] == PackageManager.PERMISSION_DENIED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                    Log.w(TAG, "Permission denied with rationale: $permission")
                    // Show explanation dialog
                    showPermissionRationaleDialog(permission)
                } else {
                    Log.w(TAG, "Permission denied permanently: $permission")
                    // Show settings dialog
                    showPermissionSettingsDialog(permission)
                }
            }
        }
    }
    
    /**
     * Show rationale dialog for permission
     */
    private fun showPermissionRationaleDialog(permission: String) {
        val explanation = getPermissionExplanation(permission)
        
        android.app.AlertDialog.Builder(activity)
            .setTitle("Permission Required")
            .setMessage(explanation)
            .setPositiveButton("Grant Permission") { _, _ ->
                requestAllPermissions()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Show settings dialog for permanently denied permission
     */
    private fun showPermissionSettingsDialog(permission: String) {
        val explanation = getPermissionExplanation(permission)
        
        android.app.AlertDialog.Builder(activity)
            .setTitle("Permission Required")
            .setMessage("$explanation\n\nPlease enable this permission in Settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Get user-friendly explanation for permission
     */
    private fun getPermissionExplanation(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> 
                "Wype needs microphone access to listen for your emergency phrase and protect your device."
            
            Manifest.permission.SEND_SMS -> 
                "Wype needs SMS permission to send emergency alerts to your trusted contacts."
            
            Manifest.permission.READ_CONTACTS -> 
                "Wype needs contacts access to help you select emergency contacts easily."
            
            Manifest.permission.POST_NOTIFICATIONS -> 
                "Wype needs notification permission to show you when the security service is active."
            
            else -> "This permission is required for Wype to function properly and protect your device."
        }
    }
    
    /**
     * Open app settings
     */
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
        }
    }
    
    /**
     * Get missing permissions summary
     */
    fun getMissingPermissionsSummary(): String {
        val missing = mutableListOf<String>()
        
        if (!hasRuntimePermissions()) missing.add("Runtime permissions")
        if (!isDeviceAdminEnabled()) missing.add("Device admin")
        if (!hasOverlayPermission()) missing.add("Overlay permission")
        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(activity)) {
            missing.add("Battery optimization exemption")
        }
        
        return if (missing.isEmpty()) {
            "All permissions granted"
        } else {
            "Missing: ${missing.joinToString(", ")}"
        }
    }
}
