package com.wype.security.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import com.wype.security.R
import com.wype.security.accessibility.WypeAccessibilityService

/**
 * Helper class for managing accessibility service permissions and setup
 */
class AccessibilityHelper {

    companion object {
        private const val TAG = "AccessibilityHelper"

        /**
         * Check if WYPE accessibility service is enabled
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            
            val serviceName = "${context.packageName}/${WypeAccessibilityService::class.java.name}"
            
            return enabledServices.any { serviceInfo ->
                serviceInfo.id == serviceName
            }
        }

        /**
         * Alternative method using Settings.Secure
         */
        fun isAccessibilityServiceEnabledSecure(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            
            if (enabledServices.isNullOrEmpty()) {
                return false
            }
            
            val serviceName = "${context.packageName}/${WypeAccessibilityService::class.java.name}"
            return enabledServices.contains(serviceName)
        }

        /**
         * Get the current status of the accessibility service
         */
        fun getAccessibilityServiceStatus(context: Context): ServiceStatus {
            return when {
                isAccessibilityServiceEnabled(context) -> {
                    val serviceInstance = WypeAccessibilityService.getInstance()
                    if (serviceInstance != null) {
                        ServiceStatus.ENABLED_AND_RUNNING
                    } else {
                        ServiceStatus.ENABLED_NOT_RUNNING
                    }
                }
                else -> ServiceStatus.DISABLED
            }
        }

        /**
         * Show dialog to guide user to enable accessibility service
         */
        fun showEnableAccessibilityDialog(context: Context, onPositive: (() -> Unit)? = null) {
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.accessibility_permission_title))
                .setMessage(
                    context.getString(R.string.accessibility_permission_message) + "\n\n" +
                    context.getString(R.string.accessibility_enable_instruction)
                )
                .setPositiveButton(context.getString(R.string.accessibility_settings_button)) { _, _ ->
                    openAccessibilitySettings(context)
                    onPositive?.invoke()
                }
                .setNegativeButton(context.getString(R.string.cancel), null)
                .setCancelable(false)
                .show()
        }

        /**
         * Open accessibility settings
         */
        fun openAccessibilitySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general settings if accessibility settings not available
                try {
                    val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                } catch (fallbackE: Exception) {
                    // If all else fails, do nothing
                }
            }
        }

        /**
         * Check if accessibility services are available on this device
         */
        fun isAccessibilityServiceSupported(context: Context): Boolean {
            return try {
                val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                accessibilityManager != null
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Get list of enabled accessibility services
         */
        fun getEnabledAccessibilityServices(context: Context): List<String> {
            return try {
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                
                if (enabledServices.isNullOrEmpty()) {
                    emptyList()
                } else {
                    enabledServices.split(':').filter { it.isNotEmpty() }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        /**
         * Get user-friendly status message
         */
        fun getStatusMessage(context: Context): String {
            return when (getAccessibilityServiceStatus(context)) {
                ServiceStatus.ENABLED_AND_RUNNING -> {
                    context.getString(R.string.accessibility_service_enabled)
                }
                ServiceStatus.ENABLED_NOT_RUNNING -> {
                    "Accessibility service is enabled but not running"
                }
                ServiceStatus.DISABLED -> {
                    context.getString(R.string.accessibility_service_disabled)
                }
            }
        }

        /**
         * Request accessibility permission with proper flow
         */
        fun requestAccessibilityPermission(
            context: Context,
            onGranted: (() -> Unit)? = null,
            onDenied: (() -> Unit)? = null
        ) {
            if (isAccessibilityServiceEnabled(context)) {
                onGranted?.invoke()
                return
            }

            showEnableAccessibilityDialog(context) {
                // User was directed to settings
                // We can't directly detect when they return, but we can provide instructions
                
                // In a real implementation, you might want to use activity results or
                // periodically check the permission status when the app resumes
                onDenied?.invoke() // For now, assume they need to manually enable it
            }
        }

        /**
         * Check if device settings allow accessibility services
         */
        fun canEnableAccessibilityService(context: Context): Boolean {
            return try {
                // Check if accessibility settings are accessible
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                val activities = context.packageManager.queryIntentActivities(intent, 0)
                activities.isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Get service statistics if running
         */
        fun getServiceStatistics(context: Context): Map<String, Any>? {
            val service = WypeAccessibilityService.getInstance()
            return service?.getServiceStatus()
        }

        /**
         * Send command to accessibility service if running
         */
        fun sendCommandToService(context: Context, action: String): Boolean {
            return try {
                val intent = Intent(context, WypeAccessibilityService::class.java).apply {
                    this.action = action
                }
                context.startService(intent)
                true
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Start wake word detection via accessibility service
         */
        fun startWakeWordDetection(context: Context): Boolean {
            return sendCommandToService(context, WypeAccessibilityService.ACTION_START_WAKE_WORD_DETECTION)
        }

        /**
         * Stop wake word detection via accessibility service
         */
        fun stopWakeWordDetection(context: Context): Boolean {
            return sendCommandToService(context, WypeAccessibilityService.ACTION_STOP_WAKE_WORD_DETECTION)
        }

        /**
         * Trigger emergency action via accessibility service
         */
        fun triggerEmergency(context: Context): Boolean {
            return sendCommandToService(context, WypeAccessibilityService.ACTION_TRIGGER_EMERGENCY)
        }

        /**
         * Monitor accessibility service status changes
         */
        fun monitorAccessibilityServiceStatus(
            context: Context,
            onStatusChanged: (ServiceStatus) -> Unit
        ) {
            // This is a simplified version. In a production app, you might want to:
            // 1. Use a background thread to periodically check status
            // 2. Listen for system accessibility service state changes
            // 3. Use lifecycle-aware components
            
            val initialStatus = getAccessibilityServiceStatus(context)
            onStatusChanged(initialStatus)
        }

        /**
         * Get comprehensive setup instructions
         */
        fun getSetupInstructions(context: Context): List<String> {
            return listOf(
                "1. Open device Settings",
                "2. Navigate to Accessibility",
                "3. Find 'WYPE Emergency Protection'",
                "4. Toggle the service ON",
                "5. Confirm in the permission dialog",
                "6. Return to WYPE app",
                "7. The service will start automatically"
            )
        }

        /**
         * Check if the service needs to be restarted
         */
        fun needsServiceRestart(context: Context): Boolean {
            val isEnabled = isAccessibilityServiceEnabled(context)
            val isRunning = WypeAccessibilityService.getInstance() != null
            
            // Service is enabled but not running
            return isEnabled && !isRunning
        }
    }

    /**
     * Accessibility service status enumeration
     */
    enum class ServiceStatus {
        DISABLED,               // Service not enabled in settings
        ENABLED_NOT_RUNNING,    // Service enabled but not active
        ENABLED_AND_RUNNING     // Service enabled and active
    }
}
