package com.wype.security.hotword

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Helper class for managing WypeHotwordService
 * Provides convenient methods to start/stop the silent hotword detection service
 */
object HotwordServiceHelper {
    
    private const val TAG = "HotwordServiceHelper"
    
    /**
     * Start the silent hotword detection service
     * @param context Application context
     */
    fun startHotwordService(context: Context) {
        try {
            val intent = Intent(context, WypeHotwordService::class.java).apply {
                action = WypeHotwordService.ACTION_START_HOTWORD_DETECTION
            }
            
            context.startForegroundService(intent)
            Log.i(TAG, "Silent hotword service start requested")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting hotword service", e)
        }
    }
    
    /**
     * Stop the silent hotword detection service  
     * @param context Application context
     */
    fun stopHotwordService(context: Context) {
        try {
            val intent = Intent(context, WypeHotwordService::class.java).apply {
                action = WypeHotwordService.ACTION_STOP_HOTWORD_DETECTION
            }
            
            context.startService(intent)
            Log.i(TAG, "Silent hotword service stop requested")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping hotword service", e)
        }
    }
    
    /**
     * Check if the hotword service is currently running
     * @return true if service is running, false otherwise
     */
    fun isServiceRunning(): Boolean {
        return WypeHotwordService.isRunning()
    }
    
    /**
     * Example usage in an Activity or Fragment:
     * 
     * ```kotlin
     * // Start the service
     * HotwordServiceHelper.startHotwordService(this)
     * 
     * // Check if running
     * if (HotwordServiceHelper.isServiceRunning()) {
     *     Log.d("Example", "Hotword service is active")
     * }
     * 
     * // Stop the service  
     * HotwordServiceHelper.stopHotwordService(this)
     * ```
     */
}
