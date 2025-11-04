package com.wype.security.hotword

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.wype.security.WypeApp
import com.wype.security.ui.MainActivity
import java.util.concurrent.atomic.AtomicLong

/**
 * Silent Foreground Service for Hotword Detection
 * 
 * Features:
 * - Completely silent operation (no beeps, vibrations, or visual alerts)
 * - Uses IMPORTANCE_LOW notification channel
 * - Runs in foreground for background processing reliability
 * - No Toast messages or heads-up notifications
 */
class WypeHotwordService : Service() {

    companion object {
        private const val TAG = "WypeHotwordService"
        private const val NOTIFICATION_ID = 1001
        
        // Service Actions
        const val ACTION_START_HOTWORD_DETECTION = "com.wype.security.START_HOTWORD"
        const val ACTION_STOP_HOTWORD_DETECTION = "com.wype.security.STOP_HOTWORD"
        
        // Service state tracking
        @Volatile
        private var isServiceRunning = false
        
        fun isRunning(): Boolean = isServiceRunning
        
        // False positive prevention parameters
        private const val SERVICE_LEVEL_DEAD_TIME_MS = 2000L      // 2 second dead time at service level
        private const val MAX_DETECTIONS_PER_MINUTE = 3           // Rate limiting
        private const val MEDIA_VOLUME_THRESHOLD = 0.7f           // Service-level volume threshold
    }

    private var isHotwordDetectionActive = false
    
    // KWS Engine integration
    private var hotwordEngine: HotwordEngine? = null
    
    // Wake lock for keeping CPU active during detection
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Service lifecycle tracking
    private var isExplicitStop = false
    
    // Service-level false positive prevention
    private val lastServiceDetectionTime = AtomicLong(0L)
    private var detectionCount = 0
    private var detectionWindowStartTime = 0L
    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WypeHotwordService created")
        isServiceRunning = true
        
        // Initialize hotword engine on service creation
        initializeHotwordEngine()
        
        // Acquire wake lock for 24/7 operation
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_HOTWORD_DETECTION -> {
                startHotwordDetection()
            }
            ACTION_STOP_HOTWORD_DETECTION -> {
                stopHotwordDetection()
                stopSelf()
            }
            else -> {
                // Default behavior - start hotword detection
                startHotwordDetection()
            }
        }
        
        // Return START_STICKY to restart if killed by system
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "WypeHotwordService destroyed - isExplicitStop: $isExplicitStop")
        isServiceRunning = false
        
        // Clean up resources
        stopHotwordDetection()
        releaseWakeLock()
        
        // Schedule restart unless this was an explicit stop
        if (!isExplicitStop) {
            Log.w(TAG, "Service destroyed unexpectedly - scheduling restart")
            scheduleServiceRestart()
        }
    }
    
    /**
     * Handle task removal - restart service to maintain 24/7 operation
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "App task removed - restarting hotword service for 24/7 operation")
        
        // Immediately restart the service
        restartServiceNow()
    }

    /**
     * Start hotword detection with silent foreground notification
     */
    private fun startHotwordDetection() {
        if (isHotwordDetectionActive) {
            Log.d(TAG, "Hotword detection already active")
            return
        }

        try {
            Log.i(TAG, "Starting silent hotword detection service")
            
            // Start foreground service with completely silent notification
            val notification = createSilentNotification()
            startForeground(NOTIFICATION_ID, notification)
            
            // Initialize hotword detection here
            // TODO: Add your hotword detection logic (Porcupine, TensorFlow, etc.)
            initializeHotwordDetection()
            
            isHotwordDetectionActive = true
            Log.i(TAG, "Silent hotword detection started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting hotword detection", e)
            stopSelf()
        }
    }

    /**
     * Stop hotword detection
     */
    private fun stopHotwordDetection() {
        if (!isHotwordDetectionActive) {
            Log.d(TAG, "Hotword detection not active")
            return
        }

        try {
            Log.i(TAG, "Stopping hotword detection")
            
            // Cleanup hotword detection resources
            cleanupHotwordDetection()
            
            isHotwordDetectionActive = false
            Log.i(TAG, "Hotword detection stopped")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping hotword detection", e)
        }
    }

    /**
     * Initialize the swappable hotword engine
     */
    private fun initializeHotwordEngine() {
        try {
            Log.i(TAG, "Initializing KWS hotword engine...")
            
            // Create Porcupine-style engine (swappable with other implementations)
            hotwordEngine = PorcupineHotword(this)
            
            // Set up callback for hotword detection events
            val callback = object : HotwordEngine.HotwordCallback {
                override fun onArmingPhrase(keyword: String, confidence: Float) {
                    handleArmingPhrase(keyword, confidence)
                }
                
                override fun onEmergencyPhrase(keyword: String, confidence: Float) {
                    handleEmergencyPhrase(keyword, confidence)
                }
                
                override fun onError(error: String, exception: Throwable?) {
                    Log.e(TAG, "Hotword engine error: $error", exception)
                }
                
                override fun onEngineStateChanged(isListening: Boolean) {
                    Log.d(TAG, "Hotword engine state changed - listening: $isListening")
                }
            }
            
            // Initialize the engine
            if (hotwordEngine?.initialize(callback) == true) {
                Log.i(TAG, "✅ Hotword engine initialized successfully")
            } else {
                Log.e(TAG, "❌ Failed to initialize hotword engine")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing hotword engine", e)
        }
    }
    
    /**
     * Initialize hotword detection components
     */
    private fun initializeHotwordDetection() {
        try {
            Log.i(TAG, "Starting hotword detection engine...")
            
            // Start the KWS engine
            if (hotwordEngine?.start() == true) {
                Log.i(TAG, "🎙️ Hotword engine started successfully")
                
                // Log current engine status
                val engineInfo = hotwordEngine?.getEngineInfo()
                Log.d(TAG, "Engine info: $engineInfo")
                
            } else {
                Log.e(TAG, "❌ Failed to start hotword engine")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting hotword detection", e)
        }
    }

    /**
     * Cleanup hotword detection resources
     */
    private fun cleanupHotwordDetection() {
        try {
            Log.i(TAG, "Cleaning up hotword detection resources...")
            
            // Stop and release the hotword engine
            hotwordEngine?.stop()
            hotwordEngine?.release()
            hotwordEngine = null
            
            Log.i(TAG, "✅ Hotword detection resources cleaned up")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up hotword detection", e)
        }
    }

    /**
     * Create a completely silent notification for foreground service
     * Uses the wype_silent channel created in WypeApp
     */
    private fun createSilentNotification(): Notification {
        // Create intent for when user taps notification (optional)
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            notificationIntent, 
            pendingIntentFlags
        )

        return NotificationCompat.Builder(this, WypeApp.WYPE_SILENT_CHANNEL_ID)
            .setContentTitle("") // Empty title for minimal visibility
            .setContentText("") // Empty text for minimal visibility
            .setSmallIcon(android.R.drawable.ic_media_play) // Minimal system icon
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Can't be dismissed by user swipe
            .setPriority(NotificationCompat.PRIORITY_MIN) // Lowest priority
            .setCategory(NotificationCompat.CATEGORY_SERVICE) // Service category
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // Hide from lock screen
            .setShowWhen(false) // Don't show timestamp
            .setOnlyAlertOnce(true) // Don't alert on updates
            .setLocalOnly(true) // Don't sync to wearables
            .setColor(Color.TRANSPARENT) // Transparent color
            .setAutoCancel(false) // Don't auto-cancel
            // Force silent notification
            .setSilent(true)
            .setVibrate(null) // No vibration
            .setSound(null) // No sound
            .setDefaults(0) // No default behaviors (sound, vibration, lights)
            .build()
    }

    /**
     * Handle hotword detection and trigger emergency alert
     * Uses AlertGate for cooldown and WorkManager for reliable delivery
     * Includes service-level guardrails for additional false positive prevention
     */
    fun onHotwordDetected(hotword: String, confidence: Float) {
        try {
            Log.w(TAG, "🚨 HOTWORD DETECTED: '$hotword' (confidence: $confidence)")
            
            // Apply service-level guardrails before proceeding
            if (!passesServiceGuardrails(hotword, confidence)) {
                Log.d(TAG, "Hotword detection blocked by service-level guardrails")
                return
            }
            
            // Check if protection mode is armed for context
            val isProtectionArmed = ProtectionMode.isArmed(this)
            Log.i(TAG, "Protection mode status: ${if (isProtectionArmed) "ARMED" else "DISARMED"}")
            
            // Check alert gate to prevent spam (3-minute cooldown)
            if (!AlertGate.isOpen()) {
                Log.w(TAG, "Hotword detected but alert gate is closed - ${AlertGate.getStatus()}")
                return
            }
            
            // Update service-level detection tracking
            updateDetectionTracking()
            
            // Create input data for the alert worker
            val inputData = SendAlertWorker.createInputData(
                alertType = SendAlertWorker.ALERT_TYPE_HOTWORD,
                protectionMode = isProtectionArmed,
                message = "Emergency hotword '$hotword' detected with ${(confidence * 100).toInt()}% confidence"
            )
            
            // Create unique work request
            val workRequest = OneTimeWorkRequestBuilder<SendAlertWorker>()
                .setInputData(inputData)
                .addTag("hotword_alert")
                .addTag("emergency")
                .build()
            
            // Enqueue with unique work name to prevent duplicate alerts
            WorkManager.getInstance(this)
                .enqueueUniqueWork(
                    "emergency_hotword_alert", // Unique work name
                    ExistingWorkPolicy.KEEP,    // Keep existing work if already queued
                    workRequest
                )
            
            Log.w(TAG, "🚨 Emergency alert worker enqueued successfully (passed all guardrails)")
            Log.i(TAG, "Alert details - Hotword: '$hotword', Confidence: $confidence, Protection: $isProtectionArmed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error handling hotword detection", e)
            // CRITICAL: No user-facing error messages, toasts, or notifications
            // All errors are logged only for debugging
        }
    }
    
    /**
     * Service-level guardrails for additional false positive prevention
     * Applied on top of engine-level guardrails for maximum protection
     */
    private fun passesServiceGuardrails(hotword: String, confidence: Float): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // 1. Service-level dead-time check (2 seconds)
        val timeSinceLastDetection = currentTime - lastServiceDetectionTime.get()
        if (timeSinceLastDetection < SERVICE_LEVEL_DEAD_TIME_MS) {
            Log.d(TAG, "Service guardrail BLOCKED: Dead-time (${timeSinceLastDetection}ms < ${SERVICE_LEVEL_DEAD_TIME_MS}ms)")
            return false
        }
        
        // 2. Rate limiting: Maximum detections per minute
        if (!passesRateLimit(currentTime)) {
            Log.d(TAG, "Service guardrail BLOCKED: Rate limit exceeded")
            return false
        }
        
        // 3. Service-level media volume check
        if (!passesServiceMediaVolumeCheck()) {
            Log.d(TAG, "Service guardrail BLOCKED: Media volume too high (service level)")
            return false
        }
        
        // 4. Protection mode double-check for emergency phrases
        if (isEmergencyPhrase(hotword) && !ProtectionMode.isArmed(this)) {
            Log.d(TAG, "Service guardrail BLOCKED: Emergency phrase but protection not armed")
            return false
        }
        
        Log.d(TAG, "Service guardrails PASSED: '$hotword'")
        return true
    }
    
    /**
     * Check if this is an emergency phrase vs arming phrase
     */
    private fun isEmergencyPhrase(keyword: String): Boolean {
        // Simple check - in real implementation would use engine keyword lists
        val emergencyWords = listOf("help", "emergency", "assistance", "danger")
        return emergencyWords.any { keyword.contains(it, ignoreCase = true) }
    }
    
    /**
     * Rate limiting check - prevent spam detections
     */
    private fun passesRateLimit(currentTime: Long): Boolean {
        // Reset counter if window has expired (60 seconds)
        if (currentTime - detectionWindowStartTime > 60000) {
            detectionCount = 0
            detectionWindowStartTime = currentTime
        }
        
        return detectionCount < MAX_DETECTIONS_PER_MINUTE
    }
    
    /**
     * Service-level media volume check with stricter threshold
     */
    private fun passesServiceMediaVolumeCheck(): Boolean {
        return try {
            val musicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxMusicVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val musicVolumePercent = if (maxMusicVolume > 0) musicVolume.toFloat() / maxMusicVolume else 0f
            
            val mediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            val maxMediaVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
            val mediaVolumePercent = if (maxMediaVolume > 0) mediaVolume.toFloat() / maxMediaVolume else 0f
            
            val highestVolume = maxOf(musicVolumePercent, mediaVolumePercent)
            val passes = highestVolume < MEDIA_VOLUME_THRESHOLD
            
            if (!passes) {
                Log.d(TAG, "Service media volume too high: ${(highestVolume*100).toInt()}%")
            }
            
            passes
        } catch (e: Exception) {
            Log.w(TAG, "Error checking service media volume, allowing", e)
            true
        }
    }
    
    /**
     * Update detection tracking for rate limiting
     */
    private fun updateDetectionTracking() {
        val currentTime = System.currentTimeMillis()
        lastServiceDetectionTime.set(currentTime)
        detectionCount++
        
        Log.v(TAG, "Detection tracking updated - Count: $detectionCount")
    }
    
    /**
     * Simulate hotword detection for testing purposes
     * Remove this method in production
     */
    fun simulateHotwordDetection(testHotword: String = "help") {
        Log.d(TAG, "🧪 Simulating hotword detection for testing")
        onHotwordDetected(testHotword, 0.95f)
    }
    
    /**
     * Handle arming phrase detection
     * Arms protection mode when arming phrase is detected
     * COMPLETELY SILENT - no user notifications whatsoever
     */
    private fun handleArmingPhrase(keyword: String, confidence: Float) {
        try {
            Log.w(TAG, "🛡️ ARMING PHRASE DETECTED: '$keyword' (confidence: $confidence)")
            
            // Check current protection mode state
            val wasArmed = ProtectionMode.isArmed(this)
            
            if (!wasArmed) {
                // Arm protection mode SILENTLY
                ProtectionMode.setArmed(this, true)
                Log.w(TAG, "🛡️ PROTECTION MODE ARMED by arming phrase (SILENT)")
                
                // Start ProtectionModeService for double-confirmation logic
                startProtectionModeService()
                
                // Log the arming event (DEBUG ONLY - no user notification)
                Log.i(TAG, "Protection mode activated silently - arming phrase: '$keyword'")
                
                // CRITICAL: No Toast, no Notification, no Sound, no Vibration
                // The arming is completely invisible to the user
                
            } else {
                Log.d(TAG, "Protection mode already armed - ignoring arming phrase")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling arming phrase (silent)", e)
            // CRITICAL: No user-facing error indication
        }
    }
    
    /**
     * Handle emergency phrase detection
     * Only triggers emergency if protection mode is armed
     * COMPLETELY SILENT - no user notifications whatsoever
     */
    private fun handleEmergencyPhrase(keyword: String, confidence: Float) {
        try {
            Log.w(TAG, "🚨 EMERGENCY PHRASE DETECTED: '$keyword' (confidence: $confidence)")
            
            // Check if protection mode is armed
            val isArmed = ProtectionMode.isArmed(this)
            
            if (isArmed) {
                Log.w(TAG, "🚨 Protection mode is ARMED - triggering emergency alert (SILENT)")
                
                // Trigger emergency alert using existing method (which has its own guardrails)
                onHotwordDetected(keyword, confidence)
                
                // CRITICAL: No Toast, no Notification, no Sound, no Vibration
                // The emergency alert is sent completely silently
                
            } else {
                Log.i(TAG, "Protection mode is NOT armed - ignoring emergency phrase (SILENT)")
                Log.i(TAG, "Arming phrase required first to enable protection mode")
                
                // CRITICAL: No user indication that emergency was ignored
                // This maintains complete stealth operation
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling emergency phrase (silent)", e)
            // CRITICAL: No user-facing error indication
        }
    }
    
    /**
     * Get service status for debugging/monitoring
     */
    fun getServiceStatus(): Map<String, Any> {
        val engineInfo = hotwordEngine?.getEngineInfo() ?: emptyMap<String, Any>()
        
        return mapOf(
            "isRunning" to isServiceRunning,
            "isHotwordActive" to isHotwordDetectionActive,
            "notificationId" to NOTIFICATION_ID,
            "channelId" to WypeApp.WYPE_SILENT_CHANNEL_ID,
            "protectionArmed" to ProtectionMode.isArmed(this),
            "alertGateStatus" to AlertGate.getStatus(),
            "alertGateOpen" to AlertGate.isOpen(),
            "engineInfo" to engineInfo,
            "engineListening" to (hotwordEngine?.isListening() ?: false)
        )
    }
    
    /**
     * Manual trigger for arming phrase (testing only)
     */
    fun simulateArmingPhrase() {
        Log.d(TAG, "🧪 Manually triggering arming phrase")
        (hotwordEngine as? PorcupineHotword)?.simulateArmingPhrase()
    }
    
    /**
     * Manual trigger for emergency phrase (testing only)
     */
    fun simulateEmergencyPhrase() {
        Log.d(TAG, "🧪 Manually triggering emergency phrase")
        (hotwordEngine as? PorcupineHotword)?.simulateEmergencyPhrase()
    }
    
    // ========================================
    // LIFECYCLE HARDENING UTILITY METHODS
    // ========================================
    
    /**
     * Acquire wake lock to keep CPU active during hotword detection
     */
    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                Log.d(TAG, "Wake lock already held")
                return
            }
            
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WypeHotwordService::HotwordDetectionWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            
            Log.i(TAG, "✅ Wake lock acquired for 24/7 hotword detection")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock", e)
        }
    }
    
    /**
     * Release wake lock when service is stopping
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let { wl ->
                if (wl.isHeld) {
                    wl.release()
                    Log.i(TAG, "Wake lock released")
                }
            }
            wakeLock = null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
    }
    
    /**
     * Schedule service restart using AlarmManager for reliability
     */
    private fun scheduleServiceRestart() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val restartIntent = Intent(this, WypeHotwordService::class.java).apply {
                action = ACTION_START_HOTWORD_DETECTION
            }
            
            val pendingIntent = PendingIntent.getService(
                this,
                1001,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val restartTime = System.currentTimeMillis() + 5000 // 5 second delay
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    restartTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    restartTime,
                    pendingIntent
                )
            }
            
            Log.w(TAG, "📅 Service restart scheduled in 5 seconds")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling service restart", e)
        }
    }
    
    /**
     * Immediately restart the service (used for task removal)
     */
    private fun restartServiceNow() {
        try {
            val restartIntent = Intent(this, WypeHotwordService::class.java).apply {
                action = ACTION_START_HOTWORD_DETECTION
            }
            
            startForegroundService(restartIntent)
            Log.w(TAG, "🔄 Service restarted immediately")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting service immediately", e)
            // Fallback to scheduled restart
            scheduleServiceRestart()
        }
    }
    
    /**
     * Check microphone permission and handle gracefully
     */
    private fun checkMicrophonePermission(): Boolean {
        val permission = ContextCompat.checkSelfPermission(
            this, 
            android.Manifest.permission.RECORD_AUDIO
        )
        
        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "❌ Microphone permission not granted - hotword detection disabled")
            return false
        }
        
        return true
    }
    
    /**
     * Mark service stop as explicit to prevent restart
     */
    private fun markExplicitStop() {
        isExplicitStop = true
        Log.d(TAG, "Service stop marked as explicit")
    }
    
    /**
     * Start ProtectionModeService for double-confirmation logic
     */
    private fun startProtectionModeService() {
        try {
            val protectionIntent = Intent(this, com.wype.security.service.ProtectionModeService::class.java).apply {
                action = com.wype.security.service.ProtectionModeService.ACTION_START_PROTECTION
            }
            
            // Start as foreground service for continuous protection monitoring
            startForegroundService(protectionIntent)
            
            Log.w(TAG, "🛡️ ProtectionModeService started for double-confirmation logic")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting ProtectionModeService", e)
        }
    }
}
