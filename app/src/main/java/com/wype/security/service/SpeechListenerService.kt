package com.wype.security.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.app.KeyguardManager
import android.view.WindowManager
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.wype.security.R
import com.wype.security.WypeApp
import com.wype.security.admin.WypeDeviceAdminReceiver
import com.wype.security.ui.MainActivity
import com.wype.security.utils.PreferencesManager
import java.util.*

/**
 * Background service for continuous speech recognition
 */
class SpeechListenerService : Service(), RecognitionListener {

    companion object {
        private const val TAG = "SpeechListenerService"
        private const val NOTIFICATION_ID = 1
        private const val DETECTION_TIMEOUT = 10000L // 10 seconds for double detection
        const val ACTION_START_LISTENING = "START_LISTENING"
        const val ACTION_STOP_LISTENING = "STOP_LISTENING"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var preferencesManager: PreferencesManager? = null
    private val handler = Handler(Looper.getMainLooper())
    
    // Wake lock and screen management
    private var wakeLock: PowerManager.WakeLock? = null
    private var keyguardManager: KeyguardManager? = null
    private var audioManager: AudioManager? = null
    private var originalRingerMode: Int = AudioManager.RINGER_MODE_NORMAL
    
    // Azure Speech Service
    private var azureSpeechService: AzureSpeechService? = null
    private var isUsingAzureSpeech = false
    
    // Double detection logic
    private var firstDetectionTime: Long = 0
    private var detectionCount = 0
    private val resetDetectionRunnable = Runnable {
        resetDetectionCount()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        preferencesManager = PreferencesManager(this)
        initializeSpeechRecognizer()
        initializeWakeLock()
        initializeAudioManager()
        // Temporarily disable Azure Speech to isolate crash issue
        // initializeAzureSpeech()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_LISTENING -> {
                if (hasPermissions()) {
                    startForegroundService()
                    startListening()
                    // Start watchdog to monitor service health
                    ServiceWatchdog.startWatchdog(this)
                } else {
                    Log.e(TAG, "Missing required permissions")
                    stopSelf()
                }
            }
            ACTION_STOP_LISTENING -> {
                stopListening()
                stopForeground(true)
                stopSelf()
            }
            else -> {
                // Default behavior - start listening if permissions available
                if (hasPermissions()) {
                    startForegroundService()
                    startListening()
                } else {
                    stopSelf()
                }
            }
        }
        
        return START_STICKY
    }

    private fun hasPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the truly silent ongoing notification
        val notification: Notification = NotificationCompat.Builder(this, WypeApp.WYPE_SILENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle("Wype is protecting you")
            .setContentText("Listening for your emergency phrase")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true) // Suppress any sound on updates
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setAutoCancel(false) // Prevent accidental dismissal
            .setShowWhen(false) // Don't show time
            .setUsesChronometer(false) // Don't show running time
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            // Use offline speech recognition if available for better silence
            speechRecognizer = try {
                SpeechRecognizer.createSpeechRecognizer(this, null)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create offline recognizer, using default", e)
                SpeechRecognizer.createSpeechRecognizer(this)
            }
            speechRecognizer?.setRecognitionListener(this)
            Log.d(TAG, "Speech recognizer initialized for silent operation")
        } else {
            Log.e(TAG, "Speech recognition not available on this device")
        }
    }
    
    private fun initializeWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Wype::SpeechListenerWakeLock"
            )
            keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            Log.d(TAG, "Wake lock initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize wake lock", e)
        }
    }
    
    private fun initializeAudioManager() {
        try {
            audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            // Store original ringer mode to restore later if needed
            originalRingerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
            Log.d(TAG, "Audio manager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio manager", e)
        }
    }
    
    private fun initializeAzureSpeech() {
        try {
            Log.d(TAG, "Attempting to initialize Azure Speech Service")
            
            // Try to create Azure Speech Service with defensive initialization
            azureSpeechService = try {
                AzureSpeechService(this)
            } catch (e: Exception) {
                Log.w(TAG, "Azure Speech Service creation failed - continuing without Azure", e)
                null
            }
            
            azureSpeechService?.let { service ->
                try {
                    service.setCallback(object : AzureSpeechService.AzureSpeechCallback {
                        override fun onSpeechRecognized(text: String) {
                            Log.d(TAG, "Azure Speech recognized: $text")
                            checkForWakePhrase(text)
                        }
                        
                        override fun onError(error: String) {
                            Log.w(TAG, "Azure Speech error: $error")
                            // Fallback to local speech recognition on error
                            if (isUsingAzureSpeech) {
                                Log.i(TAG, "Falling back to local speech recognition")
                                fallbackToLocalSpeech()
                            }
                        }
                        
                        override fun onStatusChanged(isListening: Boolean) {
                            Log.d(TAG, "Azure Speech status changed: $isListening")
                        }
                    })
                    Log.d(TAG, "Azure Speech service initialized successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set Azure Speech callback - disabling Azure", e)
                    azureSpeechService = null
                }
            }
            
            if (azureSpeechService == null) {
                Log.i(TAG, "Azure Speech not available - will use local speech recognition only")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during Azure Speech initialization", e)
            azureSpeechService = null
        }
    }

    private fun startListening() {
        if (isListening) return
        
        // Determine which speech recognition mode to use
        val recognitionMode = preferencesManager?.getSpeechRecognitionMode() ?: PreferencesManager.SpeechRecognitionMode.AUTO
        
        when (recognitionMode) {
            PreferencesManager.SpeechRecognitionMode.AZURE_COGNITIVE -> {
                startAzureSpeechListening()
            }
            PreferencesManager.SpeechRecognitionMode.LOCAL_ANDROID -> {
                startLocalSpeechListening()
            }
            PreferencesManager.SpeechRecognitionMode.AUTO -> {
                // Try Azure first, fallback to local if not available
                if (azureSpeechService?.isConfigured() == true) {
                    startAzureSpeechListening()
                } else {
                    startLocalSpeechListening()
                }
            }
        }
    }
    
    private fun startAzureSpeechListening() {
        try {
            Log.d(TAG, "Starting Azure Speech recognition")
            if (azureSpeechService?.startListening() == true) {
                isListening = true
                isUsingAzureSpeech = true
                Log.d(TAG, "Azure Speech recognition started successfully")
            } else {
                Log.w(TAG, "Failed to start Azure Speech, falling back to local")
                fallbackToLocalSpeech()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Azure Speech recognition", e)
            fallbackToLocalSpeech()
        }
    }
    
    private fun startLocalSpeechListening() {
        if (speechRecognizer == null) return
        
        // Temporarily reduce system volume to minimize beeps
        muteSystemSounds()
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            
            // Critical: Add calling package to avoid UI sounds
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            
            // Disable all audio feedback and sounds
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
            
            // Prefer offline to avoid network sounds and beeps
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            
            // Try to disable audio prompts (device-dependent)
            putExtra("android.speech.extra.DICTATION_MODE", true)
            putExtra("android.speech.extra.AUDIO_SOURCE", android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION)
            putExtra("android.speech.extra.AUDIO_INPUT_ENCODING", "ENCODING_PCM_16BIT")
        }
        
        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            isUsingAzureSpeech = false
            Log.d(TAG, "Started local speech recognition (silent mode)")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting local speech recognition", e)
            // Restore system sounds even if starting failed
            restoreSystemSounds()
        }
    }
    
    private fun fallbackToLocalSpeech() {
        Log.i(TAG, "Falling back to local speech recognition")
        isUsingAzureSpeech = false
        azureSpeechService?.stopListening()
        startLocalSpeechListening()
    }

    private fun stopListening() {
        if (isUsingAzureSpeech) {
            azureSpeechService?.stopListening()
        } else {
            speechRecognizer?.stopListening()
            // Restore system sounds for local speech
            restoreSystemSounds()
        }
        
        isListening = false
        isUsingAzureSpeech = false
        handler.removeCallbacks(resetDetectionRunnable)
        
        Log.d(TAG, "Stopped listening")
    }

    private fun restartListening() {
        handler.postDelayed({
            if (!isListening) {
                startListening()
            }
        }, 1000) // 1 second delay before restarting
    }

    // RecognitionListener implementations
    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "Ready for speech")
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "Beginning of speech detected")
    }

    override fun onRmsChanged(rmsdB: Float) {
        // RMS change - can be used for audio level indicators
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        // Audio buffer received
    }

    override fun onEndOfSpeech() {
        Log.d(TAG, "End of speech")
    }

    override fun onError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech input matched"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error"
        }
        
        Log.w(TAG, "Speech recognition error: $errorMessage")
        
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                Log.e(TAG, "Microphone permission lost - attempting to recover")
                handleMicrophonePermissionLoss()
            }
            SpeechRecognizer.ERROR_AUDIO -> {
                Log.w(TAG, "Audio error - reinitializing speech recognizer")
                reinitializeSpeechRecognizer()
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                Log.w(TAG, "Recognizer busy - waiting longer before restart")
                isListening = false
                handler.postDelayed({
                    if (!isListening) {
                        startListening()
                    }
                }, 5000) // Wait 5 seconds for busy state to clear
            }
            else -> {
                isListening = false
                restartListening()
            }
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        
        if (matches != null && matches.isNotEmpty()) {
            val spokenText = matches[0]
            Log.d(TAG, "Speech recognized: $spokenText")
            
            checkForWakePhrase(spokenText)
        }
        
        isListening = false
        restartListening()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (matches != null && matches.isNotEmpty()) {
            val partialText = matches[0]
            Log.v(TAG, "Partial result: $partialText")
            
            // Check partial results for faster detection
            checkForWakePhrase(partialText)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        Log.d(TAG, "Speech event: $eventType")
    }

    private fun checkForWakePhrase(spokenText: String) {
        val savedPhrase = preferencesManager?.getWakePhrase()
        
        if (savedPhrase.isNullOrEmpty()) {
            Log.d(TAG, "No wake phrase configured")
            return
        }
        
        // Simple similarity check (can be enhanced)
        if (isPhraseSimilar(spokenText.lowercase(), savedPhrase.lowercase())) {
            Log.i(TAG, "Wake phrase detected!")
            handleWakePhraseDetection()
        }
    }
    
    private fun isPhraseSimilar(spoken: String, target: String): Boolean {
        // Simple contains check - can be enhanced with fuzzy matching
        val targetWords = target.split(" ")
        val spokenWords = spoken.split(" ")
        
        // Check if most target words are present in spoken text
        val matchedWords = targetWords.count { targetWord ->
            spokenWords.any { spokenWord ->
                spokenWord.contains(targetWord) || targetWord.contains(spokenWord)
            }
        }
        
        return matchedWords >= (targetWords.size * 0.7).toInt() // 70% match threshold
    }

    private fun handleWakePhraseDetection() {
        val currentTime = System.currentTimeMillis()
        
        when (detectionCount) {
            0 -> {
                // First detection
                firstDetectionTime = currentTime
                detectionCount = 1
                Log.i(TAG, "First wake phrase detection - waiting for second")
                
                // Set timeout to reset detection
                handler.postDelayed(resetDetectionRunnable, DETECTION_TIMEOUT)
            }
            1 -> {
                // Second detection
                if (currentTime - firstDetectionTime <= DETECTION_TIMEOUT) {
                    Log.w(TAG, "DOUBLE WAKE PHRASE DETECTED - TRIGGERING EMERGENCY SEQUENCE")
                    triggerEmergencySequence()
                } else {
                    Log.i(TAG, "Second detection too late, resetting")
                    resetDetectionCount()
                    handleWakePhraseDetection() // Treat as first detection
                }
            }
        }
    }
    
    private fun resetDetectionCount() {
        detectionCount = 0
        firstDetectionTime = 0
        handler.removeCallbacks(resetDetectionRunnable)
        Log.d(TAG, "Detection count reset")
    }

    private fun triggerEmergencySequence() {
        Log.w(TAG, "EMERGENCY SEQUENCE TRIGGERED!")
        
        // Step 0: Wake device and screen if locked
        wakeUpDevice()
        
        // Step 1: Send emergency SMS to buddy
        sendEmergencySms()
        
        // Step 2: Perform Google backup if enabled
        if (preferencesManager?.isBackupEnabled() == true) {
            Log.i(TAG, "Backup enabled - initiating emergency Google backup")
            performEmergencyBackup()
        } else {
            Log.i(TAG, "Backup not enabled - proceeding directly to device wipe")
            // If no backup, proceed directly to factory reset after a short delay
            handler.postDelayed({
                performFactoryReset()
            }, 5000) // 5 second delay to allow SMS to be sent
        }
        
        // Log the emergency event
        val timestamp = System.currentTimeMillis()
        preferencesManager?.logPhraseDetection("EMERGENCY SEQUENCE TRIGGERED", timestamp)
        
        resetDetectionCount()
    }
    
    private fun sendEmergencySms() {
        Log.w(TAG, "LEGACY SERVICE: Emergency SMS blocked - use accessibility service instead")
        
        // DISABLED: Legacy service should not trigger emergency SMS
        // Only the WypeAccessibilityService should trigger emergency actions
        // This prevents false triggering from deprecated services
        
        Log.i(TAG, "Please enable WYPE Accessibility Service for reliable emergency detection")
    }

    private fun performEmergencyBackup() {
        Log.w(TAG, "Starting emergency backup before device wipe")
        
        try {
            val backupIntent = Intent(this, GoogleBackupService::class.java).apply {
                action = GoogleBackupService.ACTION_START_EMERGENCY_BACKUP
            }
            startService(backupIntent)
            
            // After starting backup, wait for it to complete before wiping device
            // The backup service will trigger the factory reset when complete
            Log.i(TAG, "Emergency backup initiated - factory reset will follow")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start emergency backup - proceeding with factory reset", e)
            // If backup fails, proceed with factory reset after delay
            handler.postDelayed({
                performFactoryReset()
            }, 3000) // 3 second delay
        }
    }
    
    private fun performFactoryReset() {
        Log.w(TAG, "INITIATING FACTORY RESET - DEVICE WILL BE WIPED")
        
        try {
            val resetIntent = Intent("com.wype.security.FACTORY_RESET").apply {
                setClass(this@SpeechListenerService, WypeDeviceAdminReceiver::class.java)
            }
            sendBroadcast(resetIntent)
            
            Log.w(TAG, "Factory reset broadcast sent")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger factory reset", e)
            
            // Fallback - try direct device admin approach
            try {
                WypeDeviceAdminReceiver.performFactoryReset(this)
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Fallback factory reset also failed", fallbackError)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun wakeUpDevice() {
        try {
            // Acquire wake lock to wake the device
            wakeLock?.let { wl ->
                if (!wl.isHeld) {
                    wl.acquire(30000) // Hold for 30 seconds
                    Log.d(TAG, "Wake lock acquired")
                }
            }
            
            // Turn on screen and dismiss keyguard if possible
            keyguardManager?.let { km ->
                if (km.isKeyguardLocked) {
                    Log.d(TAG, "Device is locked - attempting to wake screen")
                    
                    // Create an intent to bring the app to foreground
                    val intent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            // For Android 8.1+ use setShowWhenLocked and setTurnScreenOn in Activity
                            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        } else {
                            @Suppress("DEPRECATION")
                            addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                            @Suppress("DEPRECATION")
                            addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
                        }
                    }
                    
                    try {
                        startActivity(intent)
                        Log.d(TAG, "Emergency activity started")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start emergency activity", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wake up device", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        
        // Release wake lock
        wakeLock?.let { wl ->
            if (wl.isHeld) {
                try {
                    wl.release()
                    Log.d(TAG, "Wake lock released")
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing wake lock", e)
                }
            }
        }
        
        stopListening()
        speechRecognizer?.destroy()
        azureSpeechService?.destroy()
        handler.removeCallbacks(resetDetectionRunnable)
    }
    
    /**
     * Handle microphone permission loss by attempting recovery
     */
    private fun handleMicrophonePermissionLoss() {
        Log.w(TAG, "Handling microphone permission loss")
        
        // Check if permission is actually lost
        if (hasPermissions()) {
            Log.d(TAG, "Permission seems to be available - reinitializing")
            reinitializeSpeechRecognizer()
            return
        }
        
        // Try to trigger a permission request through MainActivity
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("REQUEST_MIC_PERMISSION", true)
            }
            startActivity(intent)
            Log.i(TAG, "Started activity to request microphone permission")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start permission request activity", e)
        }
        
        // Schedule periodic permission checks
        handler.postDelayed({
            checkAndRecoverPermissions()
        }, 10000) // Check again in 10 seconds
    }
    
    /**
     * Reinitialize the speech recognizer in case of audio errors
     */
    private fun reinitializeSpeechRecognizer() {
        Log.i(TAG, "Reinitializing speech recognizer")
        
        try {
            // Destroy current recognizer
            speechRecognizer?.destroy()
            
            // Wait a moment before reinitializing
            handler.postDelayed({
                initializeSpeechRecognizer()
                if (speechRecognizer != null) {
                    isListening = false
                    startListening()
                } else {
                    Log.e(TAG, "Failed to reinitialize speech recognizer")
                    // Try again in 30 seconds
                    handler.postDelayed({
                        reinitializeSpeechRecognizer()
                    }, 30000)
                }
            }, 2000) // Wait 2 seconds
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during speech recognizer reinitialization", e)
        }
    }
    
    /**
     * Periodically check and attempt to recover permissions
     */
    private fun checkAndRecoverPermissions() {
        if (hasPermissions()) {
            Log.i(TAG, "Microphone permission recovered - restarting service")
            reinitializeSpeechRecognizer()
        } else {
            Log.w(TAG, "Microphone permission still not available - scheduling next check")
            // Try again in 30 seconds
            handler.postDelayed({
                checkAndRecoverPermissions()
            }, 30000)
        }
    }
    
    /**
     * Temporarily mute system sounds to prevent beeps during speech recognition
     */
    private fun muteSystemSounds() {
        try {
            audioManager?.let { am ->
                // Store original volume levels
                val originalNotificationVolume = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                val originalSystemVolume = am.getStreamVolume(AudioManager.STREAM_SYSTEM)
                val originalRingVolume = am.getStreamVolume(AudioManager.STREAM_RING)
                val originalAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
                val originalDtmfVolume = am.getStreamVolume(AudioManager.STREAM_DTMF)
                
                // Temporarily mute ALL system sounds that could beep
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)
                am.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)
                am.setStreamVolume(AudioManager.STREAM_RING, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)
                am.setStreamVolume(AudioManager.STREAM_ALARM, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)
                am.setStreamVolume(AudioManager.STREAM_DTMF, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)
                
                // Additional ringer mode silencing
                originalRingerMode = am.ringerMode
                am.ringerMode = AudioManager.RINGER_MODE_SILENT
                
                Log.d(TAG, "System sounds temporarily muted for speech recognition")
                
                // Schedule restore after 30 seconds as fallback
                handler.postDelayed({
                    restoreSystemSounds()
                }, 30000)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to mute system sounds", e)
        }
    }
    
    /**
     * Restore system sound levels
     */
    private fun restoreSystemSounds() {
        try {
            audioManager?.let { am ->
                // Get max volumes for each stream
                val maxNotificationVolume = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
                val maxSystemVolume = am.getStreamMaxVolume(AudioManager.STREAM_SYSTEM)
                val maxRingVolume = am.getStreamMaxVolume(AudioManager.STREAM_RING)
                
                // Restore to reasonable levels (about 70% of max)
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, (maxNotificationVolume * 0.7).toInt(), 0)
                am.setStreamVolume(AudioManager.STREAM_SYSTEM, (maxSystemVolume * 0.7).toInt(), 0)
                am.setStreamVolume(AudioManager.STREAM_RING, (maxRingVolume * 0.7).toInt(), 0)
                
                Log.d(TAG, "System sounds restored")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to restore system sounds", e)
        }
    }
}
