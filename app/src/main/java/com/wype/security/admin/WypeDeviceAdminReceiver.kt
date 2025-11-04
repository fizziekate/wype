package com.wype.security.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wype.security.utils.PreferencesManager

/**
 * Device admin receiver for factory reset capability
 */
class WypeDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "WypeDeviceAdmin"
        
        /**
         * Check if device admin is currently enabled
         */
        fun isAdminEnabled(context: Context): Boolean {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = ComponentName(context, WypeDeviceAdminReceiver::class.java)
            return devicePolicyManager.isAdminActive(componentName)
        }
        
        /**
         * Get the component name for this device admin receiver
         */
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, WypeDeviceAdminReceiver::class.java)
        }
        
        /**
         * Perform factory reset - USE WITH EXTREME CAUTION!
         */
        fun performFactoryReset(context: Context): Boolean {
            return try {
                val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val componentName = ComponentName(context, WypeDeviceAdminReceiver::class.java)
                
                if (devicePolicyManager.isAdminActive(componentName)) {
                    Log.w(TAG, "INITIATING FACTORY RESET - THIS WILL WIPE THE DEVICE!")
                    
                    // Wipe data with external storage
                    devicePolicyManager.wipeData(
                        DevicePolicyManager.WIPE_EXTERNAL_STORAGE or 
                        DevicePolicyManager.WIPE_RESET_PROTECTION_DATA
                    )
                    true
                } else {
                    Log.e(TAG, "Device admin not active - cannot perform factory reset")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing factory reset", e)
                false
            }
        }
        
        /**
         * Force lock the device immediately
         */
        fun lockDevice(context: Context): Boolean {
            return try {
                val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val componentName = ComponentName(context, WypeDeviceAdminReceiver::class.java)
                
                if (devicePolicyManager.isAdminActive(componentName)) {
                    devicePolicyManager.lockNow()
                    Log.i(TAG, "Device locked")
                    true
                } else {
                    Log.e(TAG, "Device admin not active - cannot lock device")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error locking device", e)
                false
            }
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin enabled - factory reset capability activated")
        
        // Update preferences to indicate admin is enabled
        val preferencesManager = PreferencesManager(context)
        preferencesManager.setDeviceAdminEnabled(true)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device admin disabled - factory reset capability deactivated")
        
        // Update preferences to indicate admin is disabled
        val preferencesManager = PreferencesManager(context)
        preferencesManager.setDeviceAdminEnabled(false)
    }
    
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling WYPE device admin will prevent emergency factory reset functionality. Are you sure?"
    }
    
    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        Log.d(TAG, "Device password changed")
    }
    
    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.d(TAG, "Device password failed")
    }
    
    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.d(TAG, "Device password succeeded")
    }
}
