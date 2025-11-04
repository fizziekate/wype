package com.wype.security.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.wype.security.R
import com.wype.security.ml.HybridWakeWordManager
import com.wype.security.service.EmergencySmsService
import com.wype.security.service.ProtectionModeManager
import com.wype.security.ui.MainActivity
import com.wype.security.utils.PreferencesManager
import kotlinx.coroutines.*

/**
 * WYPE Accessibility Service
 * 
 * Provides system-level background operation with enhanced reliability:
 * - Better background processing survival
 * - System-level permissions
 * - Enhanced wake word detection capabilities
 * - Improved battery optimization resistance
 * - Access to system events and notifications
 */
class WypeAccessibilityService : AccessibilityService(), CoroutineScope {

    companion object {
        private const val TAG = "WypeAccessibilityService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "WYPE_ACCESSIBILITY_CHANNEL"
        
        // Service actions
        const val ACTION_START_WAKE_WORD_DETECTION = "com.wype.security.START_WAKE_WORD_DETECTION"
        const val ACTION_STOP_WAKE_WORD_DETECTION = "com.wype.security.STOP_WAKE_WORD_DETECTION"
        const val ACTION_TRIGGER_EMERGENCY = "com.wype.security.TRIGGER_EMERGENCY"
        const val ACTION_ENTER_PROTECTION_MODE = "com.wype.security.ENTER_PROTECTION_MODE"
        const val EXTRA_RECORDED_PHRASE = "recorded_phrase"
        
        // Static reference to service instance
        @Volatile
        private var instance: WypeAccessibilityService? = null
        
        fun getInstance(): WypeAccessibilityService? = instance
        
        fun isServiceEnabled(context: Context): Boolean {
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            val serviceName = "${context.packageName}/${WypeAccessibilityService::class.java.name}"
            return enabledServices?.contains(serviceName) == true
        }
    }

    // Coroutine management
    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job

    // Core components
    private lateinit var preferencesManager: PreferencesManager
    private var hybridWakeWordManager: HybridWakeWordManager? = null
    private var notificationManager: NotificationManager? = null
    private lateinit var protectionModeManager: ProtectionModeManager
    
    // Service state
    private var isWakeWordDetectionActive = false
    private var lastEmergencyTriggerTime = 0L
    private val emergencyCooldownMs = 300000L // 5 MINUTE cooldown to prevent spam
    private var hasShownStartupNotification = false
    private var emergencyTriggeredToday = false
    private var lastEmergencyDate = ""
    private var emergencyCountToday = 0
    private val maxEmergenciesPerDay = 3 // Maximum 3 emergencies per day
    
    // Double confirmation system
    private var firstWakeWordTime = 0L
    private var wakeWordConfirmationCount = 0
    private val confirmationTimeoutMs = 30000L // 30 seconds to say wake word twice
    private val resetConfirmationRunnable = Runnable {
        resetWakeWordConfirmation()
    }
    
    // Wake word debounce system to prevent rapid duplicate detections
    private var lastWakeWordDetectionTime = 0L
    private val wakeWordDebounceMs = 2000L // 2 seconds minimum between wake word detections
    
    // Accessibility event tracking
    private var lastScreenState = true // true = screen on
    private var isDeviceLocked = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
        Log.i(TAG, "WYPE Accessibility Service connected")
        
        try {
            // Initialize components
            preferencesManager = PreferencesManager(this)
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            protectionModeManager = ProtectionModeManager.getInstance(this)
            
            // Create notification channel
            createNotificationChannel()
            
            // Initialize protection mode if previously enabled
            protectionModeManager.initializeOnStartup()
            
            // Configure accessibility service
            configureAccessibilityService()
            
            // Start foreground service with completely invisible notification
            startForeground(NOTIFICATION_ID, createInvisibleNotification())
            
            // Initialize wake word detection if enabled
            if (preferencesManager.isServiceEnabled()) {
                startWakeWordDetection()
            }
            
            Log.i(TAG, "WYPE Accessibility Service initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing accessibility service", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        
        Log.i(TAG, "WYPE Accessibility Service destroyed")
        
        try {
            // Stop wake word detection
            stopWakeWordDetection()
            
            // Cancel all coroutines
            job.cancel()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during service cleanup", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { handleAccessibilityEvent(it) }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
        // Service interrupted - attempt to restart wake word detection
        launch {
            delay(1000) // Wait a moment before restarting
            if (preferencesManager.isServiceEnabled()) {
                startWakeWordDetection()
            }
        }
    }

    /**
     * Handle system accessibility events
     */
    private fun handleAccessibilityEvent(event: AccessibilityEvent) {
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleWindowStateChanged(event)
                }
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                    handleNotificationEvent(event)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // Monitor for screen lock/unlock
                    handleScreenStateChanged(event)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling accessibility event", e)
        }
    }

    /**
     * Handle window state changes
     */
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val className = event.className?.toString()
        
        // Detect emergency situations based on app states
        when {
            className?.contains("EmergencyDialerActivity") == true -> {
                Log.i(TAG, "Emergency dialer detected - possible emergency situation")
                // Could trigger additional safety measures
            }
            className?.contains("InCallUI") == true -> {
                Log.i(TAG, "In-call UI detected")
                // Might want to adjust wake word sensitivity during calls
            }
        }
    }

    /**
     * Handle notification events
     */
    private fun handleNotificationEvent(event: AccessibilityEvent) {
        val notificationText = event.text?.joinToString(" ") ?: ""
        
        // Monitor for emergency-related notifications
        val emergencyKeywords = listOf("emergency", "911", "help", "danger", "alert")
        
        if (emergencyKeywords.any { notificationText.lowercase().contains(it) }) {
            Log.i(TAG, "Emergency-related notification detected: $notificationText")
            // Could trigger additional monitoring or lower wake word threshold
        }
    }

    /**
     * Handle screen state changes
     */
    private fun handleScreenStateChanged(event: AccessibilityEvent) {
        // This helps optimize wake word detection based on screen state
        val isScreenOn = event.source != null
        
        if (isScreenOn != lastScreenState) {
            lastScreenState = isScreenOn
            Log.d(TAG, "Screen state changed: ${if (isScreenOn) "ON" else "OFF"}")
            
            // Adjust wake word detection sensitivity based on screen state
            if (isScreenOn) {
                // Screen is on - user is likely active, can use more sensitive detection
                updateWakeWordSensitivity(0.8f)
            } else {
                // Screen is off - use less sensitive to avoid false positives
                updateWakeWordSensitivity(0.9f)
            }
        }
    }

    /**
     * Configure accessibility service parameters
     */
    private fun configureAccessibilityService() {
        val info = AccessibilityServiceInfo().apply {
            // Events we want to monitor
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            
            // Feedback type
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            
            // Flags for enhanced capabilities
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                   AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            
            // Package names to monitor (null means all packages)
            packageNames = null
            
            // Delay between events
            notificationTimeout = 100L
        }
        
        serviceInfo = info
        Log.d(TAG, "Accessibility service configured")
    }

    /**
     * Start wake word detection
     */
    fun startWakeWordDetection() {
        if (isWakeWordDetectionActive) {
            Log.d(TAG, "Wake word detection already active")
            return
        }

        launch {
            try {
                Log.i(TAG, "Starting wake word detection via accessibility service")
                
                // Initialize hybrid wake word manager
                hybridWakeWordManager = HybridWakeWordManager(
                    context = this@WypeAccessibilityService,
                    callback = wakeWordCallback
                )
                
                if (hybridWakeWordManager?.initialize() == true) {
                    val detectionMode = preferencesManager.getMLDetectionMode()
                    val performanceProfile = preferencesManager.getMLPerformanceProfile()
                    val wakeWord = preferencesManager.getWakePhrase() ?: "wype"
                    
                    // Convert enum values to HybridWakeWordManager enums
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
                    
                    if (hybridWakeWordManager?.startDetection(hybridMode, hybridProfile, wakeWord) == true) {
                        isWakeWordDetectionActive = true
                        
                        // Show one-time startup notification
                        if (!hasShownStartupNotification) {
                            showStartupNotification()
                            hasShownStartupNotification = true
                        }
                        
                        Log.i(TAG, "Wake word detection started successfully")
                    } else {
                        Log.e(TAG, "Failed to start wake word detection")
                        // No notification updates for silent operation
                    }
                } else {
                    Log.e(TAG, "Failed to initialize wake word manager")
                    // No notification updates for silent operation
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting wake word detection", e)
                // No notification updates for silent operation
            }
        }
    }

    /**
     * Stop wake word detection
     */
    fun stopWakeWordDetection() {
        if (!isWakeWordDetectionActive) {
            Log.d(TAG, "Wake word detection not active")
            return
        }

        try {
            Log.i(TAG, "Stopping wake word detection")
            
            hybridWakeWordManager?.stopDetection()
            hybridWakeWordManager?.release()
            hybridWakeWordManager = null
            
            isWakeWordDetectionActive = false
            // No notification updates for silent operation
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping wake word detection", e)
        }
    }

    /**
     * Wake word detection callback with double confirmation requirement
     */
    private val wakeWordCallback = object : HybridWakeWordManager.WakeWordManagerCallback {
        override fun onWakeWordDetected(wakeWord: String, confidence: Float, detectorType: String) {
            Log.i(TAG, "Wake word '$wakeWord' detected with confidence $confidence using $detectorType")
            
            // Handle double confirmation logic
            handleWakeWordConfirmation(wakeWord, confidence, detectorType)
        }

        override fun onError(error: String, detectorType: String) {
            Log.e(TAG, "Wake word detection error in $detectorType: $error")
            // No notifications for silent operation
            
            // Attempt to restart detection after delay
            launch {
                delay(5000)
                if (preferencesManager.isServiceEnabled()) {
                    startWakeWordDetection()
                }
            }
        }

        override fun onDetectorSwitched(newDetector: String, reason: String) {
            Log.i(TAG, "Switched to $newDetector: $reason")
            // No notifications for silent operation
        }
    }

    /**
     * Handle wake word confirmation with double requirement and debounce protection
     */
    private fun handleWakeWordConfirmation(wakeWord: String, confidence: Float, detectorType: String) {
        val currentTime = System.currentTimeMillis()
        
        // DEBOUNCE CHECK: Prevent rapid consecutive detections
        if (currentTime - lastWakeWordDetectionTime < wakeWordDebounceMs) {
            val timeSinceLastMs = currentTime - lastWakeWordDetectionTime
            Log.d(TAG, "DEBOUNCED: Wake word detected too quickly (${timeSinceLastMs}ms ago) - ignoring to prevent echo/spam")
            return
        }
        
        // Update last detection time for debounce
        lastWakeWordDetectionTime = currentTime
        
        when (wakeWordConfirmationCount) {
            0 -> {
                // First detection - start confirmation timer
                firstWakeWordTime = currentTime
                wakeWordConfirmationCount = 1
                
                Log.w(TAG, "FIRST WAKE WORD DETECTED: '$wakeWord' (${detectorType}) - Say it again within 30 seconds to confirm emergency")
                
                // Log first detection
                preferencesManager.logPhraseDetection("FIRST: $wakeWord ($detectorType) - Waiting for confirmation", currentTime)
                
                // Schedule confirmation timeout
                launch {
                    delay(confirmationTimeoutMs)
                    resetConfirmationRunnable.run()
                }
            }
            1 -> {
                // Second detection - check if within time window
                if (currentTime - firstWakeWordTime <= confirmationTimeoutMs) {
                    Log.w(TAG, "DOUBLE CONFIRMATION COMPLETE: '$wakeWord' said twice - TRIGGERING EMERGENCY")
                    
                    // Log double confirmation
                    val timeBetween = (currentTime - firstWakeWordTime) / 1000.0
                    preferencesManager.logPhraseDetection("CONFIRMED: $wakeWord ($detectorType) - Double detection in ${timeBetween}s", currentTime)
                    
                    // Reset confirmation state
                    resetWakeWordConfirmation()
                    
                    // Proceed with emergency action
                    triggerEmergencyAction()
                } else {
                    Log.i(TAG, "Second wake word too late - treating as new first detection")
                    
                    // Too late, treat as new first detection
                    firstWakeWordTime = currentTime
                    wakeWordConfirmationCount = 1
                    
                    preferencesManager.logPhraseDetection("LATE: $wakeWord ($detectorType) - Starting new confirmation window", currentTime)
                    
                    // Schedule new timeout
                    launch {
                        delay(confirmationTimeoutMs)
                        resetConfirmationRunnable.run()
                    }
                }
            }
            else -> {
                // Should not happen, but reset if it does
                Log.w(TAG, "Invalid confirmation count: $wakeWordConfirmationCount - resetting")
                resetWakeWordConfirmation()
            }
        }
    }
    
    /**
     * Reset wake word confirmation state
     */
    private fun resetWakeWordConfirmation() {
        if (wakeWordConfirmationCount > 0) {
            Log.d(TAG, "Wake word confirmation timeout - reset to listen for first detection")
        }
        
        wakeWordConfirmationCount = 0
        firstWakeWordTime = 0L
    }

    /**
     * Trigger emergency action with comprehensive duplicate prevention
     */
    private fun triggerEmergencyAction() {
        launch {
            try {
                Log.i(TAG, "Emergency action requested - checking for duplicates/spam")
                
                // Check 1: Time-based cooldown (5 minutes)
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastEmergencyTriggerTime < emergencyCooldownMs) {
                    val remainingMinutes = ((emergencyCooldownMs - (currentTime - lastEmergencyTriggerTime)) / 60000L) + 1
                    Log.w(TAG, "EMERGENCY BLOCKED: Still in cooldown period. Wait $remainingMinutes more minutes.")
                    return@launch
                }
                
                // Check 2: Daily limit prevention
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                if (today == lastEmergencyDate) {
                    if (emergencyCountToday >= maxEmergenciesPerDay) {
                        Log.w(TAG, "EMERGENCY BLOCKED: Daily limit reached ($maxEmergenciesPerDay emergencies per day)")
                        return@launch
                    }
                } else {
                    // New day - reset counters
                    emergencyCountToday = 0
                    lastEmergencyDate = today
                    emergencyTriggeredToday = false
                }
                
                // Check 3: Buddy contact configuration
                val buddyPhone = preferencesManager.getBuddyPhone()
                val buddyName = preferencesManager.getBuddyName()
                if (buddyPhone.isNullOrEmpty() || buddyName.isNullOrEmpty()) {
                    Log.e(TAG, "EMERGENCY BLOCKED: No emergency contact configured")
                    return@launch
                }
                
                // All checks passed - proceed with emergency action
                Log.w(TAG, "EMERGENCY TRIGGERED: All checks passed, sending emergency alert")
                
                // Update tracking variables
                lastEmergencyTriggerTime = currentTime
                emergencyCountToday++
                emergencyTriggeredToday = true
                
                // Log the emergency event with detailed info
                val emergencyLog = "EMERGENCY #$emergencyCountToday triggered at ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())} - Next allowed in ${emergencyCooldownMs/60000} minutes"
                preferencesManager.logPhraseDetection(emergencyLog, currentTime)
                
                // Start emergency SMS service
                val emergencyIntent = Intent(this@WypeAccessibilityService, EmergencySmsService::class.java).apply {
                    action = EmergencySmsService.ACTION_SEND_EMERGENCY_SMS
                }
                startService(emergencyIntent)
                
                // Additional emergency actions could be added here:
                // - Take photos/videos
                // - Record audio
                // - Send location updates
                // - Trigger alarms
                // - Contact authorities
                
                Log.w(TAG, "Emergency SMS sent to $buddyName. Daily count: $emergencyCountToday/$maxEmergenciesPerDay")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during emergency action", e)
            }
        }
    }

    /**
     * Show one-time startup notification
     */
    private fun showStartupNotification() {
        launch {
            try {
                val startupNotification = createStartupNotification()
                notificationManager?.notify(NOTIFICATION_ID + 1, startupNotification)
                
                Log.i(TAG, "Startup notification shown - listening enabled")
                
                // Auto-dismiss after 5 seconds
                delay(5000)
                notificationManager?.cancel(NOTIFICATION_ID + 1)
                Log.d(TAG, "Startup notification auto-dismissed")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error showing startup notification", e)
            }
        }
    }

    /**
     * Update wake word sensitivity dynamically
     */
    private fun updateWakeWordSensitivity(sensitivity: Float) {
        // This could be implemented to adjust detection sensitivity
        // based on context (screen state, time of day, location, etc.)
        Log.d(TAG, "Adjusting wake word sensitivity to: $sensitivity")
    }

    /**
     * Create notification channel
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Android System", // Generic system name
                NotificationManager.IMPORTANCE_MIN // Lowest importance
            ).apply {
                description = "System background service"
                // COMPLETELY DISABLE SOUND AND VIBRATION
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                // Make channel completely silent and invisible
                setBypassDnd(false)
                importance = NotificationManager.IMPORTANCE_MIN
                
                // Force disable all audio/vibration attributes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Explicitly set no sound
                    setSound(null, null)
                    // Disable vibration pattern
                    vibrationPattern = null
                    enableVibration(false)
                }
                
                // Additional stealth settings
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(false)
                }
            }
            
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Create completely invisible notification
     */
    private fun createInvisibleNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("") // Empty
            .setContentText("") // Empty
            .setSmallIcon(android.R.drawable.ic_media_play) // Minimal system media icon
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
     * Create visible startup notification
     */
    private fun createStartupNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ WYPE Protection")
            .setContentText("Listening enabled - Emergency detection active")
            .setSmallIcon(android.R.drawable.ic_lock_power_off) // Security icon
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setColor(Color.parseColor("#4CAF50")) // Green color for positive confirmation
            // FORCE SILENT NOTIFICATION
            .setSilent(true)
            .setVibrate(null)
            .setSound(null)
            .setDefaults(0) // No defaults (sound, vibration, lights)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Wake word detection is now active. Emergency protection is enabled and running silently in the background.")
            )
            .build()
    }

    /**
     * Create notification (legacy method - kept for compatibility)
     */
    private fun createNotification(contentText: String, iconColor: Int = android.R.color.transparent): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("") // Empty for minimal visibility
            .setContentText("") // Empty for minimal visibility
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery) // Use system security icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setColor(Color.TRANSPARENT)
            .build()
    }

    /**
     * Update notification
     */
    private fun updateNotification(contentText: String, iconColor: Int = android.R.color.transparent) {
        try {
            val notification = createNotification(contentText, iconColor)
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }

    /**
     * Get service status information
     */
    fun getServiceStatus(): Map<String, Any> {
        return mapOf(
            "isConnected" to (instance != null),
            "isWakeWordActive" to isWakeWordDetectionActive,
            "detectorInfo" to (hybridWakeWordManager?.getCurrentDetectorInfo() ?: "None"),
            "lastScreenState" to (if (lastScreenState) "ON" else "OFF"),
            "isDeviceLocked" to isDeviceLocked
        )
    }

    /**
     * Handle external intents
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_START_WAKE_WORD_DETECTION -> {
                    startWakeWordDetection()
                }
                ACTION_STOP_WAKE_WORD_DETECTION -> {
                    stopWakeWordDetection()
                }
                ACTION_TRIGGER_EMERGENCY -> {
                    triggerEmergencyAction()
                }
                ACTION_ENTER_PROTECTION_MODE -> {
                    val recordedPhrase = intent.getStringExtra(EXTRA_RECORDED_PHRASE)
                    if (!recordedPhrase.isNullOrEmpty()) {
                        Log.w(TAG, "ENTERING PROTECTION MODE with phrase: '$recordedPhrase'")
                        protectionModeManager.enterProtectionMode(recordedPhrase)
                    } else {
                        Log.e(TAG, "Protection mode requested but no phrase provided")
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown action received: $action")
                }
            }
        }
        
        return START_STICKY
    }
}
