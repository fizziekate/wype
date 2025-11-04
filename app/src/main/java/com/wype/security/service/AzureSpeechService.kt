package com.wype.security.service

import android.content.Context
import android.util.Log
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import com.wype.security.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Azure Cognitive Services Speech-to-Text service for continuous background speech recognition
 * Provides more accurate and reliable speech recognition compared to Android's local recognizer
 */
class AzureSpeechService(private val context: Context) {

    companion object {
        private const val TAG = "AzureSpeechService"
    }

    private val preferencesManager = PreferencesManager(context)
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var onPhraseDetectedCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var onStatusChangeCallback: ((Boolean) -> Unit)? = null

    /**
     * Interface for Azure Speech recognition callbacks
     */
    interface AzureSpeechCallback {
        fun onSpeechRecognized(text: String)
        fun onError(error: String)
        fun onStatusChanged(isListening: Boolean)
    }

    /**
     * Set callback for speech recognition events
     */
    fun setCallback(callback: AzureSpeechCallback) {
        onPhraseDetectedCallback = { text -> callback.onSpeechRecognized(text) }
        onErrorCallback = { error -> callback.onError(error) }
        onStatusChangeCallback = { status -> callback.onStatusChanged(status) }
    }

    /**
     * Initialize Azure Speech Recognizer with credentials
     */
    fun initialize(): Boolean {
        try {
            Log.d(TAG, "Starting Azure Speech initialization")
            
            val speechKey = preferencesManager.getAzureSpeechKey()
            val region = preferencesManager.getAzureSpeechRegion()

            if (speechKey.isNullOrEmpty() || region.isNullOrEmpty()) {
                Log.w(TAG, "Azure Speech credentials not configured")
                return false
            }

            // Test if Azure Speech SDK is available before initializing
            try {
                Log.d(TAG, "Testing Azure Speech SDK availability")
                val testConfig = SpeechConfig.fromSubscription(speechKey, region)
                testConfig.close() // Just test creation
                Log.d(TAG, "Azure Speech SDK is available")
            } catch (e: Exception) {
                Log.e(TAG, "Azure Speech SDK not available or failed to load", e)
                return false
            }

            // Create speech configuration
            val speechConfig = SpeechConfig.fromSubscription(speechKey, region)
            speechConfig.speechRecognitionLanguage = Locale.getDefault().toLanguageTag()
            
            // Optimize for continuous recognition
            speechConfig.setProperty(PropertyId.SpeechServiceConnection_InitialSilenceTimeoutMs, "5000")
            speechConfig.setProperty(PropertyId.SpeechServiceConnection_EndSilenceTimeoutMs, "1000")
            speechConfig.setProperty(PropertyId.Speech_SegmentationSilenceTimeoutMs, "2000")

            // Create audio configuration for microphone
            val audioConfig = AudioConfig.fromDefaultMicrophoneInput()

            // Create speech recognizer
            speechRecognizer = SpeechRecognizer(speechConfig, audioConfig)

            // Set up event listeners
            setupEventListeners()

            Log.d(TAG, "Azure Speech recognizer initialized successfully")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Azure Speech recognizer", e)
            onErrorCallback?.invoke("Failed to initialize Azure Speech: ${e.message}")
            
            // Clean up any partial initialization
            try {
                speechRecognizer?.close()
                speechRecognizer = null
            } catch (cleanupError: Exception) {
                Log.w(TAG, "Error during cleanup after failed initialization", cleanupError)
            }
            
            return false
        }
    }

    /**
     * Start continuous speech recognition
     */
    fun startListening(): Boolean {
        try {
            if (speechRecognizer == null) {
                if (!initialize()) {
                    return false
                }
            }

            if (isListening) {
                Log.w(TAG, "Already listening")
                return true
            }

            val future = speechRecognizer?.startContinuousRecognitionAsync()
            future?.get(5, TimeUnit.SECONDS)

            isListening = true
            onStatusChangeCallback?.invoke(true)
            Log.d(TAG, "Started continuous Azure Speech recognition")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Azure Speech recognition", e)
            onErrorCallback?.invoke("Failed to start recognition: ${e.message}")
            return false
        }
    }

    /**
     * Stop continuous speech recognition
     */
    fun stopListening(): Boolean {
        try {
            if (!isListening) {
                return true
            }

            val future = speechRecognizer?.stopContinuousRecognitionAsync()
            future?.get(5, TimeUnit.SECONDS)

            isListening = false
            onStatusChangeCallback?.invoke(false)
            Log.d(TAG, "Stopped Azure Speech recognition")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop Azure Speech recognition", e)
            onErrorCallback?.invoke("Failed to stop recognition: ${e.message}")
            return false
        }
    }

    /**
     * Check if Azure Speech is currently listening
     */
    fun isListening(): Boolean = isListening

    /**
     * Check if Azure Speech is properly configured
     */
    fun isConfigured(): Boolean {
        return preferencesManager.isAzureSpeechConfigured() && 
               preferencesManager.isAzureSpeechEnabled()
    }

    /**
     * Release Azure Speech resources
     */
    fun destroy() {
        try {
            stopListening()
            speechRecognizer?.close()
            speechRecognizer = null
            
            onPhraseDetectedCallback = null
            onErrorCallback = null
            onStatusChangeCallback = null
            
            Log.d(TAG, "Azure Speech resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Azure Speech resources", e)
        }
    }

    /**
     * Set up event listeners for Azure Speech recognizer
     */
    private fun setupEventListeners() {
        speechRecognizer?.let { recognizer ->
            
            // Recognizing event - partial results
            recognizer.recognizing.addEventListener { _, e ->
                if (e.result.reason == ResultReason.RecognizingSpeech) {
                    val partialText = e.result.text
                    Log.v(TAG, "Azure recognizing: $partialText")
                    // Check partial results for faster phrase detection
                    if (partialText.isNotEmpty()) {
                        onPhraseDetectedCallback?.invoke(partialText)
                    }
                }
            }

            // Recognized event - final results
            recognizer.recognized.addEventListener { _, e ->
                when (e.result.reason) {
                    ResultReason.RecognizedSpeech -> {
                        val finalText = e.result.text
                        Log.d(TAG, "Azure recognized: $finalText")
                        if (finalText.isNotEmpty()) {
                            onPhraseDetectedCallback?.invoke(finalText)
                        }
                    }
                    ResultReason.NoMatch -> {
                        Log.v(TAG, "Azure: No speech could be recognized")
                    }
                    else -> {
                        Log.w(TAG, "Azure recognition result: ${e.result.reason}")
                    }
                }
            }

            // Canceled event - errors and session ended
            recognizer.canceled.addEventListener { _, e ->
                Log.w(TAG, "Azure recognition canceled: ${e.reason}")
                
                if (e.reason == CancellationReason.Error) {
                    val errorDetails = "ErrorCode: ${e.errorCode}, ErrorDetails: ${e.errorDetails}"
                    Log.e(TAG, "Azure Speech error: $errorDetails")
                    onErrorCallback?.invoke(errorDetails)
                    
                    // Try to restart recognition after error
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            Thread.sleep(2000) // Wait 2 seconds
                            if (isListening) {
                                restartRecognition()
                            }
                        } catch (ex: Exception) {
                            Log.e(TAG, "Failed to restart after error", ex)
                        }
                    }
                }
                
                if (e.reason == CancellationReason.EndOfStream) {
                    Log.d(TAG, "Azure recognition ended")
                    isListening = false
                    onStatusChangeCallback?.invoke(false)
                }
            }

            // Session started event
            recognizer.sessionStarted.addEventListener { _, e ->
                Log.d(TAG, "Azure Speech session started: ${e.sessionId}")
            }

            // Session stopped event
            recognizer.sessionStopped.addEventListener { _, e ->
                Log.d(TAG, "Azure Speech session stopped: ${e.sessionId}")
                isListening = false
                onStatusChangeCallback?.invoke(false)
            }
        }
    }

    /**
     * Restart recognition after error or interruption
     */
    private fun restartRecognition() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "Restarting Azure Speech recognition")
                
                // Stop current recognition
                stopListening()
                
                // Wait a moment
                withContext(Dispatchers.IO) {
                    Thread.sleep(1000)
                }
                
                // Reinitialize and restart
                if (initialize()) {
                    startListening()
                } else {
                    onErrorCallback?.invoke("Failed to restart Azure Speech recognition")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during recognition restart", e)
                onErrorCallback?.invoke("Restart failed: ${e.message}")
            }
        }
    }

    /**
     * Test Azure Speech configuration by performing a short recognition test
     */
    fun testConfiguration(callback: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val testSuccess = initialize()
                
                withContext(Dispatchers.Main) {
                    if (testSuccess) {
                        callback(true, "Azure Speech configuration is valid")
                    } else {
                        callback(false, "Azure Speech configuration failed")
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "Configuration test failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Get current Azure Speech service status
     */
    fun getStatus(): String {
        return when {
            !isConfigured() -> "Not configured"
            speechRecognizer == null -> "Not initialized"
            isListening -> "Listening (Azure)"
            else -> "Ready"
        }
    }
}
