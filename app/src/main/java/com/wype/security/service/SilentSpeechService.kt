package com.wype.security.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.wype.security.R
import com.wype.security.WypeApp
import com.wype.security.ui.MainActivity
import com.wype.security.utils.PreferencesManager
import com.wype.security.wakeword.WakeWordDetector
import com.wype.security.service.ProtectionModeManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import android.os.Bundle

/**
 * Silent Foreground Service for continuous wake word detection
 * Implements persistent AudioRecord session with minimal audio disturbance
 * State machine: INIT → READY → LISTENING → PROCESSING
 */
class SilentSpeechService : Service() {

    companion object {
        private const val TAG = "SilentSpeechService"
        private const val NOTIFICATION_ID = 1
        private const val DETECTION_TIMEOUT = 10000L
        
        // Audio configuration constants
        private const val SAMPLE_RATE = 16000 // 16 kHz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 4
        
        const val ACTION_START_LISTENING = "START_LISTENING"
        const val ACTION_STOP_LISTENING = "STOP_LISTENING"
        
        // Single instance protection
        @Volatile
        private var running = false
    }

    // Improved state machine with backoff
    private sealed class HState {
        data object Idle : HState()
        data object Starting : HState()
        data object Listening : HState()
        data class Error(val backoffMs: Long) : HState()
    }

    // Core components
    private var currentState: HState = HState.Idle
    private val serviceBinder = SilentSpeechBinder()
    private var preferencesManager: PreferencesManager? = null
    
    // Audio management
    private var audioRecord: AudioRecord? = null
    private var audioManager: AudioManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: Any? = null // AudioFocusRequest for API 26+
    private val isAudioFocusHeld = AtomicBoolean(false)
    
    // Speech recognition
    private var speechRecognizer: SpeechRecognizer? = null
    private val isListening = AtomicBoolean(false)
    
    // Wake word detection
    private var wakeWordDetector: WakeWordDetector? = null
    
    // Coroutine management
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var audioJob: Job? = null
    
    // Threading
    private var audioThread: HandlerThread? = null
    private var audioHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Detection logic
    private var firstDetectionTime: Long = 0
    private var detectionCount = 0
    private val resetDetectionRunnable = Runnable { resetDetectionCount() }
    
    // Backoff logic for error handling
    private var backoffRetries = 0
    private val maxBackoffRetries = 5

    inner class SilentSpeechBinder : Binder() {
        fun getService(): SilentSpeechService = this@SilentSpeechService
        fun isListening(): Boolean = isListening.get()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DEBUG: Service onCreate() called")
        
        // Single instance protection
        if (running) {
            Log.w(TAG, "Service already running - stopping duplicate")
            stopSelf()
            return
        }
        running = true
        
        currentState = HState.Starting
        preferencesManager = PreferencesManager(this)
        
        // Debug: Check wake phrase configuration
        val savedPhrase = preferencesManager?.getWakePhrase()
        Log.d(TAG, "DEBUG: Wake phrase configured: '${savedPhrase}' (${savedPhrase?.length ?: 0} chars)")
        Log.d(TAG, "DEBUG: Has wake phrase: ${preferencesManager?.hasWakePhrase()}")
        Log.d(TAG, "DEBUG: Service enabled: ${preferencesManager?.isServiceEnabled()}")
        
        initializeAudioSystem()
        initializeWakeLock()
        
        currentState = HState.Idle
        Log.d(TAG, "DEBUG: Service ready for operation, state: $currentState")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service command received: ${intent?.action}, state: $currentState")
        
        // Handle backoff delay if in error state
        if (currentState is HState.Error) {
            val errorState = currentState as HState.Error
            Log.i(TAG, "In error state with backoff: ${errorState.backoffMs}ms")
            mainHandler.postDelayed({
                currentState = HState.Idle
                // Retry starting after backoff
                if (hasPermissions()) {
                    startAudioListening()
                }
            }, errorState.backoffMs)
            return START_STICKY
        }
        
        when (intent?.action) {
            ACTION_START_LISTENING -> {
                if (hasPermissions()) {
                    startAudioListening()
                } else {
                    Log.e(TAG, "Missing required permissions")
                    handlePermissionError()
                }
            }
            ACTION_STOP_LISTENING -> {
                stopAudioListening()
                stopSelf()
            }
            else -> {
                // Default behavior - start listening if ready
                if (hasPermissions() && currentState == HState.Idle) {
                    startAudioListening()
                } else if (!hasPermissions()) {
                    handlePermissionError()
                }
            }
        }
        
        return START_STICKY // Always restart if killed
    }

    override fun onBind(intent: Intent?): IBinder = serviceBinder

    /**
     * Initialize all audio components once during service creation
     */
    private fun initializeAudioSystem() {
        try {
            audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            
            // Create dedicated audio thread
            audioThread = HandlerThread("WypeAudioThread").apply {
                start()
                audioHandler = Handler(looper)
            }
            
            // Don't initialize AudioRecord here - wait until permissions are confirmed
            // setupPersistentAudioRecord() - moved to startAudioListening()
            initializeSpeechRecognizer()
            initializeWakeWordDetector()
            
            Log.d(TAG, "Audio system initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio system", e)
            handleAudioInitError(e)
        }
    }

    /**
     * Setup persistent AudioRecord session that stays open during service lifetime
     */
    private fun setupPersistentAudioRecord() {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val actualBufferSize = bufferSize * BUFFER_SIZE_MULTIPLIER
            
            // Create AudioAttributes optimized for voice recognition
            val audioAttributes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            } else null
            
            // Setup AudioRecord with proper configuration
            // Use legacy constructor for better compatibility
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                actualBufferSize
            )
            
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                Log.d(TAG, "AudioRecord initialized successfully (buffer: ${actualBufferSize})")
            } else {
                Log.e(TAG, "AudioRecord initialization failed")
                audioRecord = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup AudioRecord", e)
            audioRecord = null
        }
    }

    /**
     * Initialize SpeechRecognizer for offline processing
     */
    private fun initializeSpeechRecognizer() {
        try {
            Log.d(TAG, "DEBUG: Initializing SpeechRecognizer...")
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(speechRecognitionListener)
                }
                Log.d(TAG, "DEBUG: SpeechRecognizer initialized successfully")
            } else {
                Log.e(TAG, "DEBUG: Speech recognition not available on this device")
            }
        } catch (e: Exception) {
            Log.e(TAG, "DEBUG: Failed to initialize SpeechRecognizer", e)
        }
    }
    
    /**
     * Initialize wake word detector
     */
    private fun initializeWakeWordDetector() {
        try {
            wakeWordDetector = WakeWordDetector(this).apply {
                // Initialize in coroutine
                serviceScope.launch {
                    val success = initialize(WakeWordDetector.DetectionEngine.AUTO)
                    Log.d(TAG, "Wake word detector initialized: $success")
                    
                    // Collect wake word events
                    detectionEvents.collect { event ->
                        Log.i(TAG, "Wake word detected: ${event.keyword} (confidence: ${event.confidence})")
                        withContext(Dispatchers.Main) {
                            handleWakeWordEvent(event)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize wake word detector", e)
        }
    }
    
    /**
     * Handle wake word detection event
     */
    private fun handleWakeWordEvent(event: WakeWordDetector.WakeWordEvent) {
        Log.i(TAG, "Processing wake word event: ${event.keyword}")
        
        // Convert wake word event to phrase detection
        checkForWakePhrase(event.keyword)
    }

    private fun initializeWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Wype::SilentSpeechWakeLock"
            )
            Log.d(TAG, "Wake lock initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize wake lock", e)
        }
    }

    /**
     * Create the silent foreground notification (called only once)
     */
    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, WypeApp.WYPE_SILENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle("Wype is active")
            .setContentText("Listening for your emergency phrase")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true) // Critical: suppress all sounds
            .build()

        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Silent foreground service started")
    }

    /**
     * Start audio listening process
     */
    private fun startAudioListening() {
        if (currentState != HState.Idle || isListening.get()) {
            Log.w(TAG, "Cannot start listening - state: $currentState, listening: ${isListening.get()}")
            return
        }

        Log.d(TAG, "Starting audio listening")
        currentState = HState.Starting
        
        // Initialize AudioRecord now that permissions are confirmed
        if (audioRecord == null) {
            setupPersistentAudioRecord()
        }
        
        // Check if AudioRecord initialization was successful
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized - cannot start listening")
            handleAudioError(SecurityException("AudioRecord initialization failed"))
            return
        }
        
        // Start as foreground service before doing audio work  
        startForegroundService()
        
        currentState = HState.Listening
        
        // Request audio focus once
        requestAudioFocus()
        
        // Acquire wake lock
        acquireWakeLock()
        
        // Start the audio processing coroutine
        audioJob = serviceScope.launch {
            try {
                processAudioContinuously()
            } catch (e: Exception) {
                Log.e(TAG, "Audio processing error", e)
                withContext(Dispatchers.Main) {
                    handleAudioError(e)
                }
            }
        }
        
        isListening.set(true)
        Log.d(TAG, "Audio listening started successfully")
    }

    /**
     * Continuous audio processing in coroutine
     */
    private suspend fun processAudioContinuously() {
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not properly initialized")
            return
        }

        withContext(Dispatchers.Default) {
            try {
                audioRecord?.startRecording()
                
                // Start speech recognition for emergency phrase detection
                withContext(Dispatchers.Main) {
                    startSpeechRecognition()
                }
                
                val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                val audioBuffer = ShortArray(bufferSize)
                
                while (isListening.get() && !currentCoroutineContext().isActive.not()) {
                    val bytesRead = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0
                    
                    if (bytesRead > 0) {
                        // Process audio buffer with wake word detector for additional wake words
                        wakeWordDetector?.processAudioBuffer(audioBuffer, bytesRead)
                        
                        // Calculate audio level for voice activity detection
                        val audioLevel = calculateAudioLevel(audioBuffer, bytesRead)
                        
                        // Log periodic audio activity (every 2 seconds)
                        if (System.currentTimeMillis() % 2000 < 50) {
                            Log.v(TAG, "Audio activity level: $audioLevel")
                        }
                    }
                    
                    delay(50) // Process every 50ms
                }
                
            } finally {
                audioRecord?.stop()
                // Stop speech recognition
                withContext(Dispatchers.Main) {
                    speechRecognizer?.stopListening()
                }
                Log.d(TAG, "Audio recording stopped")
            }
        }
    }

    private fun calculateAudioLevel(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            sum += buffer[i] * buffer[i]
        }
        return Math.sqrt(sum / length).toFloat()
    }

    private fun getVoiceActivityThreshold(): Float = 1000f // Adjustable threshold

    /**
     * Start speech recognition without Google UI
     */
    private fun startSpeechRecognition() {
        Log.d(TAG, "DEBUG: startSpeechRecognition() called")
        
        if (speechRecognizer == null) {
            Log.e(TAG, "DEBUG: SpeechRecognizer is null, cannot start")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            
            // Critical: prevent Google UI sounds
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            
            // Silence settings
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
            
            // Disable audio prompts
            putExtra("android.speech.extra.DICTATION_MODE", true)
        }

        try {
            Log.d(TAG, "DEBUG: Starting speech recognition with language: ${Locale.getDefault()}")
            speechRecognizer?.startListening(intent)
            Log.i(TAG, "DEBUG: Speech recognition started successfully for emergency phrase detection")
        } catch (e: Exception) {
            Log.e(TAG, "DEBUG: Failed to start speech recognition", e)
        }
    }

    /**
     * Stop audio listening
     */
    private fun stopAudioListening() {
        Log.d(TAG, "Stopping audio listening")
        
        isListening.set(false)
        currentState = HState.Idle
        
        // Cancel audio job
        audioJob?.cancel()
        audioJob = null
        
        // Stop speech recognition
        speechRecognizer?.stopListening()
        
        // Release audio focus
        releaseAudioFocus()
        
        // Release wake lock
        releaseWakeLock()
        
        Log.d(TAG, "Audio listening stopped")
    }

    /**
     * Request audio focus once when starting listening
     */
    private fun requestAudioFocus() {
        if (isAudioFocusHeld.get()) return

        audioManager?.let { am ->
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .setWillPauseWhenDucked(false) // Prevent OEM beeps
                    .build()
                audioFocusRequest = request
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }

            isAudioFocusHeld.set(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            Log.d(TAG, "Audio focus requested, result: $result")
        }
    }

    /**
     * Release audio focus only when service stops
     */
    private fun releaseAudioFocus() {
        if (!isAudioFocusHeld.get()) return

        audioManager?.let { am ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                am.abandonAudioFocusRequest(audioFocusRequest as android.media.AudioFocusRequest)
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusChangeListener)
            }
            isAudioFocusHeld.set(false)
            Log.d(TAG, "Audio focus released")
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained - resuming if needed")
                // Resume listening if we were paused
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost temporarily - pausing")
                // Pause without stopping completely
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost permanently")
                isAudioFocusHeld.set(false)
            }
        }
    }

    private fun acquireWakeLock() {
        wakeLock?.let { wl ->
            if (!wl.isHeld) {
                try {
                    wl.acquire(10 * 60 * 1000L) // 10 minutes timeout
                    Log.d(TAG, "Wake lock acquired")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to acquire wake lock", e)
                }
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { wl ->
            if (wl.isHeld) {
                try {
                    wl.release()
                    Log.d(TAG, "Wake lock released")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to release wake lock", e)
                }
            }
        }
    }

    private val speechRecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.v(TAG, "Speech recognizer ready")
        }

        override fun onBeginningOfSpeech() {
            Log.v(TAG, "Speech detected")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // RMS monitoring for voice activity detection
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Audio buffer received
        }

        override fun onEndOfSpeech() {
            Log.v(TAG, "End of speech")
            // Note: Speech recognition is currently disabled in favor of WakeWordDetector
        }

        override fun onError(error: Int) {
            val errorMsg = getSpeechErrorMessage(error)
            Log.w(TAG, "DEBUG: Speech recognition error: $errorMsg (code: $error)")
            
            // Auto-restart speech recognition on certain recoverable errors
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    Log.d(TAG, "DEBUG: Recoverable speech error, restarting recognition in 1 second")
                    mainHandler.postDelayed({
                        if (isListening.get() && speechRecognizer != null) {
                            Log.d(TAG, "DEBUG: Auto-restarting speech recognition")
                            startSpeechRecognition()
                        }
                    }, 1000)
                }
                else -> {
                    Log.e(TAG, "DEBUG: Non-recoverable speech error: $errorMsg")
                    mainHandler.postDelayed({
                        if (isListening.get() && speechRecognizer != null) {
                            Log.d(TAG, "DEBUG: Attempting to restart speech recognition after error")
                            startSpeechRecognition()
                        }
                    }, 3000)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            handleSpeechResults(results)
            // Note: Speech recognition is currently disabled in favor of WakeWordDetector
        }

        override fun onPartialResults(partialResults: Bundle?) {
            handleSpeechResults(partialResults)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.v(TAG, "Speech event: $eventType")
        }
    }

    private fun handleSpeechResults(results: Bundle?) {
        Log.d(TAG, "DEBUG: handleSpeechResults called with results: ${results != null}")
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        Log.d(TAG, "DEBUG: Speech matches found: ${matches?.size ?: 0}")
        
        if (!matches.isNullOrEmpty()) {
            for (i in matches.indices) {
                Log.d(TAG, "DEBUG: Speech result [$i]: '${matches[i]}'")
            }
            val spokenText = matches[0]
            Log.i(TAG, "Speech recognized: '$spokenText'")
            checkForWakePhrase(spokenText)
        } else {
            Log.w(TAG, "DEBUG: No speech matches found")
        }
    }

    private fun checkForWakePhrase(spokenText: String) {
        val savedPhrase = preferencesManager?.getWakePhrase()
        Log.d(TAG, "DEBUG: Checking wake phrase - spoken: '$spokenText', saved: '$savedPhrase'")
        
        if (savedPhrase.isNullOrEmpty()) {
            Log.w(TAG, "DEBUG: No saved wake phrase found in preferences")
            return
        }
        
        // Skip empty or very short speech results
        if (spokenText.trim().length < 3) {
            Log.d(TAG, "DEBUG: Spoken text too short, ignoring: '$spokenText'")
            return
        }

        val similarity = isPhraseSimilar(spokenText.lowercase(), savedPhrase.lowercase())
        Log.d(TAG, "DEBUG: Phrase similarity check - result: $similarity")
        
        if (similarity) {
            Log.i(TAG, "Wake phrase detected: '$spokenText' matches '$savedPhrase'")
            handleWakePhraseDetection()
        } else {
            Log.d(TAG, "DEBUG: No match - '$spokenText' vs '$savedPhrase'")
        }
    }

    private fun isPhraseSimilar(spoken: String, target: String): Boolean {
        val targetWords = target.split(" ")
        val spokenWords = spoken.split(" ")
        
        val matchedWords = targetWords.count { targetWord ->
            spokenWords.any { spokenWord ->
                spokenWord.contains(targetWord) || targetWord.contains(spokenWord)
            }
        }
        
        return matchedWords >= (targetWords.size * 0.7).toInt()
    }

    private fun handleWakePhraseDetection() {
        val currentTime = System.currentTimeMillis()
        
        when (detectionCount) {
            0 -> {
                firstDetectionTime = currentTime
                detectionCount = 1
                Log.i(TAG, "First wake phrase detection")
                mainHandler.postDelayed(resetDetectionRunnable, DETECTION_TIMEOUT)
            }
            1 -> {
                if (currentTime - firstDetectionTime <= DETECTION_TIMEOUT) {
                    Log.w(TAG, "DOUBLE WAKE PHRASE DETECTED - EMERGENCY TRIGGERED")
                    triggerEmergencySequence()
                } else {
                    Log.i(TAG, "Second detection too late, resetting")
                    resetDetectionCount()
                    handleWakePhraseDetection()
                }
            }
        }
    }

    private fun resetDetectionCount() {
        detectionCount = 0
        firstDetectionTime = 0
        mainHandler.removeCallbacks(resetDetectionRunnable)
    }

    private fun triggerEmergencySequence() {
        Log.w(TAG, "EMERGENCY SEQUENCE TRIGGERED! (default mode)")
        
        try {
            // Reuse centralized emergency pipeline, allowing without protection mode
            val manager = ProtectionModeManager.getInstance(this)
            manager.triggerEmergencyActions(allowWithoutProtectionMode = true)
            Log.w(TAG, "Default-mode emergency actions dispatched (SMS + Factory Reset)")
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching default-mode emergency actions", e)
        } finally {
            // Reset detection window regardless
            resetDetectionCount()
        }
    }


    private fun getSpeechErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech input matched"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error ($error)"
        }
    }

    private fun hasPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Handle permission errors with exponential backoff
     */
    private fun handlePermissionError() {
        Log.e(TAG, "Permission error - RECORD_AUDIO not granted")
        val backoffMs = calculateBackoffMs()
        currentState = HState.Error(backoffMs)
        
        // Don't keep retrying if we've exceeded max retries
        if (backoffRetries >= maxBackoffRetries) {
            Log.e(TAG, "Max permission retries exceeded - stopping service")
            stopSelf()
        }
    }
    
    /**
     * Handle audio initialization errors
     */
    private fun handleAudioInitError(e: Exception) {
        Log.e(TAG, "Audio initialization error", e)
        val backoffMs = calculateBackoffMs()
        currentState = HState.Error(backoffMs)
    }
    
    /**
     * Handle audio errors with exponential backoff and retry
     */
    private fun handleAudioError(e: Exception) {
        Log.e(TAG, "Audio error occurred - attempting recovery", e)
        
        val backoffMs = calculateBackoffMs()
        currentState = HState.Error(backoffMs)
        
        // Stop current audio operations
        isListening.set(false)
        audioJob?.cancel()
        audioJob = null
        
        // Reset audio resources
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        releaseAudioFocus()
        releaseWakeLock()
        
        // Don't retry if we've exceeded max attempts
        if (backoffRetries >= maxBackoffRetries) {
            Log.e(TAG, "Max audio error retries exceeded - stopping service")
            stopSelf()
            return
        }
        
        // Schedule retry with exponential backoff
        mainHandler.postDelayed({
            if (currentState is HState.Error && hasPermissions()) {
                Log.i(TAG, "Attempting audio system recovery")
                try {
                    initializeAudioSystem()
                    currentState = HState.Idle
                    startAudioListening()
                    backoffRetries = 0 // Reset on successful recovery
                } catch (recovery: Exception) {
                    Log.e(TAG, "Audio recovery failed", recovery)
                    handleAudioError(recovery)
                }
            }
        }, backoffMs)
    }
    
    /**
     * Calculate exponential backoff delay
     */
    private fun calculateBackoffMs(): Long {
        backoffRetries++
        val baseDelayMs = 1000L // 1 second base
        val maxDelayMs = 30000L // 30 seconds max
        val delayMs = (baseDelayMs * Math.pow(2.0, (backoffRetries - 1).toDouble())).toLong()
        return minOf(delayMs, maxDelayMs)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service being destroyed")
        
        // Stop all audio processing
        stopAudioListening()
        
        // Cancel all coroutines
        serviceScope.cancel()
        
        // Cleanup audio resources
        audioRecord?.release()
        speechRecognizer?.destroy()
        wakeWordDetector?.release()
        
        // Cleanup threading
        audioThread?.quitSafely()
        
        // Remove callbacks
        mainHandler.removeCallbacks(resetDetectionRunnable)
        
        // Reset single instance protection
        running = false
        
        Log.d(TAG, "Service destroyed cleanly")
    }
}
