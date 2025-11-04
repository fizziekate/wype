package com.wype.security.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.porcupine.PorcupineException
import com.wype.security.ui.MainActivity
import com.wype.security.R
import com.wype.security.service.EmergencySmsService
import com.wype.security.utils.PreferencesManager
import com.wype.security.utils.WakeWordModelManager

class PorcupineService : Service() {

    companion object {
        private const val TAG = "PorcupineService"
        private const val NOTIFICATION_ID = 1001
        // Use the same channel as WypeApp for consistency
        private const val CHANNEL_ID = "wype_silent"
        
        // Service actions
        const val ACTION_START_SERVICE = "com.wype.security.ACTION_START_PORCUPINE"
        const val ACTION_STOP_SERVICE = "com.wype.security.ACTION_STOP_PORCUPINE"
        const val ACTION_RESTART_SERVICE = "com.wype.security.ACTION_RESTART_PORCUPINE"
    }

    private var porcupineManager: PorcupineManager? = null
    private lateinit var preferencesManager: PreferencesManager
    private var isServiceRunning = false
    private var lastDetectionTime = 0L
    private val detectionCooldownMs = 2000L // 2 second cooldown between detections

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        // No need to create channel here - it's already created by WypeApplication
        Log.d(TAG, "PorcupineService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_SERVICE -> startPorcupineService()
            ACTION_STOP_SERVICE -> stopPorcupineService()
            ACTION_RESTART_SERVICE -> {
                stopPorcupineDetection()
                startPorcupineService()
            }
            else -> startPorcupineService()
        }
        
        return START_STICKY // Restart service if killed by system
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPorcupineService() {
        if (isServiceRunning) {
            Log.d(TAG, "Service already running")
            return
        }

        if (!preferencesManager.isPorcupineEnabled() || !preferencesManager.isPorcupineConfigured()) {
            Log.w(TAG, "Porcupine not enabled or configured properly")
            stopSelf()
            return
        }

        // Start foreground service with notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification("Initializing..."), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification("Initializing..."))
        }

        startPorcupineDetection()
    }

    private fun startPorcupineDetection() {
        try {
            val accessKey = preferencesManager.getPorcupineAccessKey()
            val wakeWord = preferencesManager.getPorcupineWakeWord()
            val sensitivity = preferencesManager.getPorcupineSensitivity()

            if (accessKey.isNullOrEmpty() || wakeWord.isNullOrEmpty()) {
                Log.e(TAG, "Missing required Porcupine configuration")
                updateNotification("Configuration Error", android.R.color.holo_red_dark)
                return
            }

            Log.d(TAG, "Starting Porcupine with wake word: $wakeWord, sensitivity: $sensitivity")

            // Create Porcupine manager with callback
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeyword(Porcupine.BuiltInKeyword.valueOf(wakeWord.uppercase()))
                .setSensitivity(sensitivity)
                .build(applicationContext, porcupineManagerCallback)

            // Start detection
            porcupineManager?.start()
            isServiceRunning = true

            updateNotification("Listening for '$wakeWord'", android.R.color.holo_green_dark)
            Log.i(TAG, "Porcupine wake word detection started successfully")

        } catch (e: PorcupineException) {
            Log.e(TAG, "Failed to initialize Porcupine: ${e.message}", e)
            updateNotification("Initialization Failed", android.R.color.holo_red_dark)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting Porcupine: ${e.message}", e)
            updateNotification("Unexpected Error", android.R.color.holo_red_dark)
            stopSelf()
        }
    }

    private val porcupineManagerCallback = PorcupineManagerCallback { keywordIndex ->
        val currentTime = System.currentTimeMillis()
        
        // Implement cooldown to prevent rapid-fire detections
        if (currentTime - lastDetectionTime < detectionCooldownMs) {
            Log.d(TAG, "Wake word detection ignored due to cooldown")
            return@PorcupineManagerCallback
        }
        
        lastDetectionTime = currentTime
        val wakeWord = preferencesManager.getPorcupineWakeWord() ?: "unknown"
        
        Log.i(TAG, "Wake word '$wakeWord' detected!")
        
        // Update notification temporarily
        updateNotification("Wake word detected!", android.R.color.holo_blue_bright)
        
        // Trigger emergency SMS service
        triggerEmergencyAction()
        
        // Reset notification after 3 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isServiceRunning) {
                updateNotification("Listening for '$wakeWord'", android.R.color.holo_green_dark)
            }
        }, 3000)
    }

    private fun triggerEmergencyAction() {
        try {
            Log.d(TAG, "LEGACY SERVICE: Emergency action blocked - use accessibility service instead")
            
            // DISABLED: Legacy Porcupine service should not trigger emergency SMS
            // Only the WypeAccessibilityService should trigger emergency actions
            // This prevents false triggering from deprecated services
            
            Log.i(TAG, "Please enable WYPE Accessibility Service for reliable emergency detection")
        } catch (e: Exception) {
            Log.e(TAG, "Legacy service error: ${e.message}", e)
        }
    }

    private fun stopPorcupineService() {
        Log.d(TAG, "Stopping Porcupine service")
        stopPorcupineDetection()
        stopForeground(true)
        stopSelf()
    }

    private fun stopPorcupineDetection() {
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
            porcupineManager = null
            isServiceRunning = false
            Log.i(TAG, "Porcupine detection stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Porcupine: ${e.message}", e)
        }
    }


    private fun createNotification(contentText: String, iconColor: Int = android.R.color.holo_blue_dark): Notification {
        // Intent to open main activity when notification is tapped
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("") // Empty title to be less visible
            .setContentText("") // Empty text to be minimal
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Use system microphone icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN) // Minimum priority
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true) // Ensure completely silent
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // Hide from lock screen
            .setShowWhen(false) // Don't show timestamp
            .setOnlyAlertOnce(true) // Only alert once
            .build()
    }

    private fun updateNotification(contentText: String, iconColor: Int = android.R.color.holo_blue_dark) {
        val notification = createNotification(contentText, iconColor)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPorcupineDetection()
        Log.d(TAG, "PorcupineService destroyed")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Keep service running even if app is swiped away
        Log.d(TAG, "Task removed, but service continues running")
    }
}
