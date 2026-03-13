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
 * Watchdog to keep emergency phrase detection running.
 * When protection mode is enabled, ensures ProtectionModeService is running.
 * Uses AlarmManager for reliable periodic checks (including in Doze).
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
        
        val appContext = context.applicationContext
        val preferencesManager = PreferencesManager(appContext)
        
        // Primary path: protection mode enabled → ensure ProtectionModeService is running
        if (preferencesManager.isProtectionModeEnabled() && preferencesManager.hasWakePhrase()) {
            if (!isProtectionModeServiceRunning(appContext)) {
                Log.w(TAG, "ProtectionModeService is not running - attempting to restart")
                restartProtectionModeService(appContext)
            } else {
                Log.v(TAG, "ProtectionModeService is running normally")
            }
            startWatchdog(appContext)
            return
        }
        
        // Legacy path: service enabled (no protection mode) → SpeechListenerService
        if (preferencesManager.isServiceEnabled() && preferencesManager.hasWakePhrase()) {
            if (!isSpeechListenerServiceRunning(appContext)) {
                Log.w(TAG, "SpeechListenerService is not running - attempting to restart")
                restartSpeechListenerService(appContext)
            } else {
                Log.v(TAG, "SpeechListenerService is running normally")
            }
            startWatchdog(appContext)
            return
        }
        
        Log.d(TAG, "No protection or legacy service configured - stopping watchdog")
        stopWatchdog(appContext)
    }
    
    private fun isProtectionModeServiceRunning(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        val services = activityManager.getRunningServices(Integer.MAX_VALUE)
        return services.any { it.service.className == ProtectionModeService::class.java.name }
    }
    
    private fun isSpeechListenerServiceRunning(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        val services = activityManager.getRunningServices(Integer.MAX_VALUE)
        return services.any { it.service.className == SpeechListenerService::class.java.name }
    }
    
    private fun restartProtectionModeService(context: Context) {
        try {
            val serviceIntent = Intent(context, ProtectionModeService::class.java).apply {
                action = ProtectionModeService.ACTION_START_PROTECTION
            }
            context.startForegroundService(serviceIntent)
            Log.i(TAG, "ProtectionModeService restart initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart ProtectionModeService", e)
        }
    }
    
    private fun restartSpeechListenerService(context: Context) {
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
