package com.wype.security.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.wype.security.utils.PreferencesManager

/**
 * Watchdog service to ensure SpeechListenerService stays running
 * Uses AlarmManager for reliable periodic checks
 */
class ServiceWatchdog : BroadcastReceiver() {

    companion object {
        private const val TAG = "ServiceWatchdog"
        private const val ACTION_CHECK_SERVICE = "com.wype.security.CHECK_SERVICE"
        private const val CHECK_INTERVAL = 60000L // Check every minute
        private const val REQUEST_CODE = 1001
        
        /**
         * Start the watchdog to monitor service health
         */
        fun startWatchdog(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ServiceWatchdog::class.java).apply {
                action = ACTION_CHECK_SERVICE
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Use setExactAndAllowWhileIdle for reliable execution even in Doze mode
            val triggerTime = SystemClock.elapsedRealtime() + CHECK_INTERVAL
            
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
                else -> {
                    alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            }
            
            Log.d(TAG, "Service watchdog started")
        }
        
        /**
         * Stop the watchdog
         */
        fun stopWatchdog(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ServiceWatchdog::class.java).apply {
                action = ACTION_CHECK_SERVICE
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Service watchdog stopped")
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != ACTION_CHECK_SERVICE) return
        
        Log.v(TAG, "Watchdog check triggered")
        
        val preferencesManager = PreferencesManager(context)
        
        // Only check if service should be running
        if (!preferencesManager.isServiceEnabled() || !preferencesManager.hasWakePhrase()) {
            Log.d(TAG, "Service not configured - stopping watchdog")
            stopWatchdog(context)
            return
        }
        
        // Check if SpeechListenerService is running
        if (!isServiceRunning(context)) {
            Log.w(TAG, "SpeechListenerService is not running - attempting to restart")
            restartService(context)
        } else {
            Log.v(TAG, "SpeechListenerService is running normally")
        }
        
        // Schedule next check
        startWatchdog(context)
    }
    
    /**
     * Check if the SpeechListenerService is currently running
     */
    private fun isServiceRunning(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        
        @Suppress("DEPRECATION")
        val services = activityManager.getRunningServices(Integer.MAX_VALUE)
        
        for (service in services) {
            if (SpeechListenerService::class.java.name == service.service.className) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Attempt to restart the SpeechListenerService
     */
    private fun restartService(context: Context) {
        try {
            val serviceIntent = Intent(context, SpeechListenerService::class.java).apply {
                action = SpeechListenerService.ACTION_START_LISTENING
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.i(TAG, "SpeechListenerService restart initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart SpeechListenerService", e)
        }
    }
}
