package com.wype.security.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helper class to manage battery optimization settings for reliable background operation
 */
object BatteryOptimizationHelper {
    
    private const val TAG = "BatteryOptimizationHelper"
    
    /**
     * Check if the app is whitelisted from battery optimization
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // Not applicable for older Android versions
        }
    }
    
    /**
     * Request to ignore battery optimizations for this app
     * Returns true if the intent was successfully started
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!isIgnoringBatteryOptimizations(context)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Log.d(TAG, "Battery optimization exemption requested")
                    true
                } else {
                    Log.d(TAG, "App already exempt from battery optimization")
                    true
                }
            } else {
                Log.d(TAG, "Battery optimization not applicable for this Android version")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request battery optimization exemption", e)
            false
        }
    }
    
    /**
     * Open battery optimization settings page for all apps
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.d(TAG, "Battery optimization settings opened")
                true
            } else {
                Log.d(TAG, "Battery optimization settings not available")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open battery optimization settings", e)
            false
        }
    }
    
    /**
     * Check if doze mode is likely affecting the app
     */
    fun isDeviceIdleMode(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isDeviceIdleMode
        } else {
            false
        }
    }
    
    /**
     * Get battery optimization status as a user-friendly string
     */
    fun getBatteryOptimizationStatus(context: Context): String {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> "Not applicable"
            isIgnoringBatteryOptimizations(context) -> "Optimized for background operation"
            else -> "May be limited in background"
        }
    }
}
