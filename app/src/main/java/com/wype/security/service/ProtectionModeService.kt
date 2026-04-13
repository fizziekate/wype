package com.wype.security.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.Nullable
import androidx.core.app.NotificationCompat
import com.wype.security.ml.HybridWakeWordManager
import com.wype.security.service.ProtectionModeManager
import com.wype.security.ui.MainActivity
import com.wype.security.utils.PreferencesManager
import kotlinx.coroutines.*

/**
 * Protection Mode Foreground Service
 * 
 * Runs continuously in the background with silent notifications to provide:
 * - Continuous wake word detection using on-device ML
 * - Double confirmation requirement for emergency triggering
 * - SMS, Backup, and Factory Reset emergency actions
 * - Silent operation with invisible notifications
 * - Automatic protection mode activation after phrase recording
 */
class ProtectionModeService : Service(), CoroutineScope {
    
    companion object {
        private const val TAG = "ProtectionModeService"
        
        // Service Actions
        const val ACTION_START_PROTECTION = "com.wype.security.START_PROTECTION"
        const val ACTION_STOP_PROTECTION = "com.wype.security.STOP_PROTECTION"
        
        // Notification Configuration
        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "wype_bg_service" 
        
        // Protection Parameters
        private const val CONFIRMATION_TIMEOUT_MS = 10000L // 10 seconds (Double-trigger window)
        private const val DEBOUNCE_TIME_MS = 2000L // 2 seconds between detections

    }
    
    // Coroutine management
    private val serviceJob = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + serviceJob
    
    // Core components
    private lateinit var preferencesManager: PreferencesManager
    private var hybridWakeWordManager: HybridWakeWordManager? = null
    private var notificationManager: NotificationManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Protection state
    private var isProtectionActive = false
    private var firstWakeWordTime = 0L
    private var wakeWordConfirmationCount = 0
    private var lastWakeWordDetectionTime = 0L
    
    // Reset confirmation timeout
    private val resetConfirmationRunnable = Runnable {
        resetWakeWordConfirmation()
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Protection Mode Service created")
        
        try {
            // Initialize components
            preferencesManager = PreferencesManager(this)
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            Log.i(TAG, "Protection Mode Service initialized")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Protection Mode Service", e)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PROTECTION -> {
                startProtectionMode()
            }
            ACTION_STOP_PROTECTION -> {
                stopProtectionMode()
                stopSelf()
            }
        }
        
        return START_STICKY // Restart if killed by system
    }
    
    @Nullable
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Protection Mode Service destroyed")
        
        try {
            stopProtectionMode()
            serviceJob.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error during service cleanup", e)
        }
    }
    
    /**
     * Start protection mode with continuous wake word detection
     */
    private fun startProtectionMode() {
        if (isProtectionActive) {
            Log.d(TAG, "Protection mode already active")
            return
        }
        
        launch {
            try {
                Log.w(TAG, "STARTING PROTECTION MODE - Device entering silent protection")
                
                // Load persistent confirmation state (crash resilience)
                loadConfirmationStateFromPreferences()
                
                // Start foreground service with silent notification
                // Android 14+ requires the type to be passed explicitly in code too
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startForeground(NOTIFICATION_ID, createSilentNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(NOTIFICATION_ID, createSilentNotification())
                }
                
                // Initialize wake word detection
                if (initializeWakeWordDetection()) {
                    isProtectionActive = true
                    acquireWakeLock()
                    Log.w(TAG, "PROTECTION MODE ACTIVE - Listening silently for emergency phrase")
                    ServiceWatchdog.startWatchdog(this@ProtectionModeService)
                    // Log loaded state for debugging
                    val stateStatus = preferencesManager.getConfirmationStateStatus()
                    if (stateStatus["count"] as Int > 0) {
                        Log.i(TAG, "Loaded confirmation state after restart: $stateStatus")
                    }
                } else {
                    Log.e(TAG, "Failed to initialize wake word detection - protection mode failed")
                    stopSelf()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting protection mode", e)
                stopSelf()
            }
        }
    }
    
    /**
     * Stop protection mode
     */
    private fun stopProtectionMode() {
        if (!isProtectionActive) {
            Log.d(TAG, "Protection mode not active")
            return
        }
        
        try {
            Log.i(TAG, "STOPPING PROTECTION MODE")
            
            // Stop wake word detection
            hybridWakeWordManager?.stopDetection()
            hybridWakeWordManager?.release()
            hybridWakeWordManager = null
            
            // Reset state
            isProtectionActive = false
            releaseWakeLock()
            resetWakeWordConfirmation()
            ServiceWatchdog.stopWatchdog(this)
            Log.i(TAG, "Protection mode stopped")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping protection mode", e)
        }
    }
    
    /**
     * Initialize wake word detection with hybrid ML system
     */
    private fun initializeWakeWordDetection(): Boolean {
        return try {
            val wakePhrase = preferencesManager.getWakePhrase()
            if (wakePhrase.isNullOrEmpty()) {
                Log.e(TAG, "No wake phrase configured - cannot start detection")
                return false
            }
            
            Log.i(TAG, "Initializing wake word detection for protection mode")
            
            // Initialize hybrid wake word manager
            hybridWakeWordManager = HybridWakeWordManager(
                context = this,
                callback = protectionWakeWordCallback
            )
            
            if (hybridWakeWordManager?.initialize() == true) {
                val detectionMode = preferencesManager.getMLDetectionMode()
                val performanceProfile = preferencesManager.getMLPerformanceProfile()
                
                // Convert enum values
                val hybridMode = when (detectionMode) {
                    PreferencesManager.MLDetectionMode.PORCUPINE -> 
                        HybridWakeWordManager.Companion.DetectionMode.PORCUPINE
                    PreferencesManager.MLDetectionMode.TENSORFLOW_LITE -> 
                        HybridWakeWordManager.Companion.DetectionMode.TENSORFLOW_LITE
                    PreferencesManager.MLDetectionMode.SIMPLE_NEURAL -> 
                        HybridWakeWordManager.Companion.DetectionMode.SIMPLE_NEURAL
                    PreferencesManager.MLDetectionMode.AUTO -> 
                        HybridWakeWordManager.Companion.DetectionMode.AUTO
                }
                
                val hybridProfile = when (performanceProfile) {
                    PreferencesManager.MLPerformanceProfile.ULTRA_LOW_POWER -> 
                        HybridWakeWordManager.Companion.PerformanceProfile.ULTRA_LOW_POWER
                    PreferencesManager.MLPerformanceProfile.BALANCED -> 
                        HybridWakeWordManager.Companion.PerformanceProfile.BALANCED
                    PreferencesManager.MLPerformanceProfile.HIGH_ACCURACY -> 
                        HybridWakeWordManager.Companion.PerformanceProfile.HIGH_ACCURACY
                }
                
                if (hybridWakeWordManager?.startDetection(hybridMode, hybridProfile, wakePhrase) == true) {
                    Log.i(TAG, "Wake word detection started successfully for protection mode")
                    return true
                } else {
                    Log.e(TAG, "Failed to start wake word detection")
                    return false
                }
            } else {
                Log.e(TAG, "Failed to initialize wake word manager")
                return false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing wake word detection", e)
            false
        }
    }
    
    /**
     * Wake word callback for protection mode
     */
    private val protectionWakeWordCallback = object : HybridWakeWordManager.WakeWordManagerCallback {
        override fun onWakeWordDetected(wakeWord: String, confidence: Float, detectorType: String) {
            Log.w(TAG, "PROTECTION: Wake word '$wakeWord' detected with confidence $confidence")
            handleProtectionWakeWordConfirmation(wakeWord, confidence, detectorType)
        }

        override fun onError(error: String, detectorType: String) {
            Log.e(TAG, "Protection wake word detection error in $detectorType: $error")
            
            // Attempt to restart detection after delay
            launch {
                delay(5000)
                if (isProtectionActive) {
                    initializeWakeWordDetection()
                }
            }
        }

        override fun onDetectorSwitched(newDetector: String, reason: String) {
            Log.i(TAG, "Protection detector switched to $newDetector: $reason")
        }
    }
    
    /**
     * Handle wake word confirmation with double requirement and debounce
     */
    private fun handleProtectionWakeWordConfirmation(wakeWord: String, confidence: Float, detectorType: String) {
        val currentTime = System.currentTimeMillis()
        
        // DEBOUNCE CHECK: Prevent rapid consecutive detections
        if (currentTime - lastWakeWordDetectionTime < DEBOUNCE_TIME_MS) {
            val timeSinceLastMs = currentTime - lastWakeWordDetectionTime
            Log.d(TAG, "PROTECTION DEBOUNCED: Wake word detected too quickly (${timeSinceLastMs}ms ago)")
            return
        }
        
        // Update last detection time
        lastWakeWordDetectionTime = currentTime
        
        when (wakeWordConfirmationCount) {
            0 -> {
                // First detection - start confirmation timer
                firstWakeWordTime = currentTime
                wakeWordConfirmationCount = 1
                
                Log.w(TAG, "PROTECTION FIRST DETECTION: '$wakeWord' - Say again within 10s for emergency")
                
                // Save confirmation state for crash resilience
                saveConfirmationStateToPreferences(wakeWord, detectorType)
                
                // Log first detection
                preferencesManager.logPhraseDetection(
                    "PROTECTION FIRST: $wakeWord ($detectorType) - Awaiting confirmation", 
                    currentTime
                )
                
                // Schedule confirmation timeout
                launch {
                    delay(CONFIRMATION_TIMEOUT_MS)
                    resetConfirmationRunnable.run()
                }
            }
            1 -> {
                // Second detection - check if within time window
                if (currentTime - firstWakeWordTime <= CONFIRMATION_TIMEOUT_MS) {
                    Log.w(TAG, "PROTECTION EMERGENCY CONFIRMED: '$wakeWord' detected twice within 10s - TRIGGERING EMERGENCY ACTIONS")
                    
                    // Log double confirmation
                    val timeBetween = (currentTime - firstWakeWordTime) / 1000.0
                    preferencesManager.logPhraseDetection(
                        "PROTECTION EMERGENCY: $wakeWord ($detectorType) - Confirmed in ${timeBetween}s - SMS+BACKUP+FACTORY_RESET", 
                        currentTime
                    )
                    
                    // Reset confirmation state
                    resetWakeWordConfirmation()
                    
                    // Trigger emergency actions: SMS + Backup + Factory Reset
                    triggerEmergencyActions()
                } else {
                    Log.i(TAG, "Protection second wake word too late - treating as new first detection")
                    
                    // Too late, treat as new first detection
                    firstWakeWordTime = currentTime
                    wakeWordConfirmationCount = 1
                    
                    preferencesManager.logPhraseDetection(
                        "PROTECTION LATE: $wakeWord ($detectorType) - New confirmation window", 
                        currentTime
                    )
                    
                    // Schedule new timeout
                    launch {
                        delay(CONFIRMATION_TIMEOUT_MS)
                        resetConfirmationRunnable.run()
                    }
                }
            }
            else -> {
                // Invalid state - reset
                Log.w(TAG, "Protection invalid confirmation count: $wakeWordConfirmationCount - resetting")
                resetWakeWordConfirmation()
            }
        }
    }
    
    /**
     * Reset wake word confirmation state
     */
    private fun resetWakeWordConfirmation() {
        if (wakeWordConfirmationCount > 0) {
            Log.d(TAG, "Protection wake word confirmation timeout - reset to listen for first detection")
        }
        
        wakeWordConfirmationCount = 0
        firstWakeWordTime = 0L
        
        // Clear persistent confirmation state
        preferencesManager.clearConfirmationState()
    }
    
    /**
     * Trigger emergency actions
     */
    private fun triggerEmergencyActions() {
        launch {
            try {
                Log.w(TAG, "PROTECTION EMERGENCY TRIGGERED - Checking conditions and executing sequence")
                
                // Validate emergency conditions
                if (!validateEmergencyConditions()) {
                    Log.e(TAG, "Emergency conditions not met - emergency actions blocked")
                    return@launch
                }
                
                // Increment emergency count
                val emergencyCount = preferencesManager.incrementProtectionModeEmergencyCount()
                
                // Log emergency event
                val timestamp = System.currentTimeMillis()
                val emergencyLog = "PROTECTION EMERGENCY #$emergencyCount - Sequence: SMS -> Backup -> Factory Reset"
                preferencesManager.logPhraseDetection(emergencyLog, timestamp)
                
                // Get ProtectionModeManager and trigger emergency actions
                val protectionManager = ProtectionModeManager.getInstance(this@ProtectionModeService)
                protectionManager.triggerEmergencyActions()
                
                Log.w(TAG, "Protection emergency sequence initiated")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during protection emergency actions", e)
            }
        }
    }
    
    /**
     * Validate emergency conditions before triggering
     */
    private fun validateEmergencyConditions(): Boolean {
        try {
            // Check if protection mode is active
            if (!preferencesManager.isProtectionModeEnabled()) {
                Log.e(TAG, "Protection mode not active")
                return false
            }

            Log.i(TAG, "Emergency conditions validated")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error validating emergency conditions", e)
            return false
        }
    }
    
    /**
     * Create completely invisible/silent notification
     */
    private fun createSilentNotification(): Notification {
        val channelId = com.wype.security.WypeApp.WYPE_PROTECTION_CHANNEL_ID
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("") 
            .setContentText("") 
            .setSmallIcon(android.R.drawable.ic_media_play) 
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setColor(Color.TRANSPARENT)
            .setAutoCancel(false)
            .setLocalOnly(true)
            .build()
    }
    
    /**
     * Acquire partial wake lock so detection can run when screen is off/locked.
     */
    private fun acquireWakeLock() {
        try {
            releaseWakeLock()
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WypeApp:ProtectionModeService"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d(TAG, "WakeLock acquired for background listening")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }
    
    /**
     * Release wake lock when protection stops.
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released")
                }
                wakeLock = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }
    }
    
    /**
     * Get protection service status
     */
    fun getProtectionStatus(): Map<String, Any> {
        return mapOf(
            "isProtectionActive" to isProtectionActive,
            "wakeWordConfirmationCount" to wakeWordConfirmationCount,
            "detectorInfo" to (hybridWakeWordManager?.getCurrentDetectorInfo() ?: "None"),
            "protectionDuration" to preferencesManager.getProtectionModeDurationHours(),
            "emergencyCountToday" to preferencesManager.getProtectionModeEmergencyCount()
        )
    }
    
    // ========================================
    // CONFIRMATION STATE PERSISTENCE HELPERS
    // ========================================
    
    private fun saveConfirmationStateToPreferences(keyword: String, detectorType: String) {
        try {
            preferencesManager.saveConfirmationState(
                count = wakeWordConfirmationCount,
                firstTime = firstWakeWordTime,
                lastTime = lastWakeWordDetectionTime,
                keyword = keyword,
                detectorType = detectorType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error saving confirmation state", e)
        }
    }
    
    private fun loadConfirmationStateFromPreferences() {
        try {
            val savedState = preferencesManager.loadConfirmationState()
            
            if (savedState.count > 0 && preferencesManager.isConfirmationStateValid(CONFIRMATION_TIMEOUT_MS)) {
                wakeWordConfirmationCount = savedState.count
                firstWakeWordTime = savedState.firstTime
                lastWakeWordDetectionTime = savedState.lastTime
                
                if (wakeWordConfirmationCount == 1) {
                    val timeRemaining = CONFIRMATION_TIMEOUT_MS - (System.currentTimeMillis() - firstWakeWordTime)
                    if (timeRemaining > 0) {
                        launch {
                            delay(timeRemaining)
                            resetConfirmationRunnable.run()
                        }
                    } else {
                        resetWakeWordConfirmation()
                    }
                }
            } else {
                if (savedState.count > 0) {
                    preferencesManager.clearConfirmationState()
                }
                wakeWordConfirmationCount = 0
                firstWakeWordTime = 0L
                lastWakeWordDetectionTime = 0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading confirmation state", e)
            wakeWordConfirmationCount = 0
            firstWakeWordTime = 0L
            lastWakeWordDetectionTime = 0L
        }
    }
}
