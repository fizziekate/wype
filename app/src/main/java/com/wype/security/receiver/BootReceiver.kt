package com.wype.security.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.wype.security.service.SpeechListenerService
import com.wype.security.utils.PreferencesManager

/**
 * Boot receiver to restart speech service after device restart
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "Boot completed, checking if service should be started")
                
                val preferencesManager = PreferencesManager(context)
                
                // Only restart service if it was previously enabled and app is fully configured
                if (preferencesManager.isServiceEnabled() && preferencesManager.hasWakePhrase()) {
                    Log.i(TAG, "Restarting SpeechListenerService after boot")
                    
                    val serviceIntent = Intent(context, SpeechListenerService::class.java).apply {
                        action = SpeechListenerService.ACTION_START_LISTENING
                    }
                    
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        Log.d(TAG, "Service restart initiated")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restart service after boot", e)
                    }
                } else {
                    Log.d(TAG, "Service not configured or was disabled - not starting")
                }
            }
        }
    }
}
