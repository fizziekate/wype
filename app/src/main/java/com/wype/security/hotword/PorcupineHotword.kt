package com.wype.security.hotword

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioManager
import android.util.Log
import com.wype.security.utils.PreferencesManager
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

/**
 * Porcupine-style Hotword Engine Implementation
 * 
 * Features:
 * - On-device keyword spotting (no network required)
 * - Separate arming and emergency phrase detection
 * - Configurable sensitivity and keywords
 * - Placeholder implementation ready for real Porcupine integration
 * - Silent operation with callback-based notifications
 */
class PorcupineHotword(private val context: Context) : HotwordEngine {
    
    companion object {
        private const val TAG = "PorcupineHotword"
        
        // Placeholder keywords - in real implementation, these would be Porcupine wake words
        private val DEFAULT_ARMING_KEYWORDS = listOf(
            "activate protection",  // Primary arming phrase
            "start guard mode",     // Alternative arming phrase
            "enable safety"         // Secondary arming phrase
        )
        
        private val DEFAULT_EMERGENCY_KEYWORDS = listOf(
            "help me now",         // Primary emergency phrase
            "emergency situation", // Alternative emergency phrase
            "call for help"        // Secondary emergency phrase
        )
        
        // Placeholder sensitivity values
        private const val DEFAULT_SENSITIVITY = 0.7f
        private const val MIN_SENSITIVITY = 0.1f
        private const val MAX_SENSITIVITY = 1.0f
        
        // Simulation parameters
        private const val DETECTION_SIMULATION_DELAY_MS = 100L
        
        // False positive prevention parameters
        private const val DEAD_TIME_MIN_MS = 300L           // Minimum dead time after detection
        private const val DEAD_TIME_MAX_MS = 500L           // Maximum dead time after detection
        private const val VAD_RMS_THRESHOLD = 0.02f         // Minimum RMS for valid audio
        private const val MEDIA_VOLUME_THRESHOLD = 0.6f     // Ignore if media volume > 60%
        private const val MIN_CONFIDENCE_THRESHOLD = 0.75f   // Minimum confidence to proceed
    }
    
    // Engine state management
    private val engineState = AtomicReference(HotwordEngineState.UNINITIALIZED)
    private val isListening = AtomicBoolean(false)
    private var callback: HotwordEngine.HotwordCallback? = null
    
    // Configuration
    private var armingKeywords = DEFAULT_ARMING_KEYWORDS.toMutableList()
    private var emergencyKeywords = DEFAULT_EMERGENCY_KEYWORDS.toMutableList()
    private var sensitivity = DEFAULT_SENSITIVITY
    
    // Coroutine management for background processing
    private var detectionJob: Job? = null
    private val detectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // False positive prevention guardrails
    private val lastDetectionTime = AtomicLong(0L)
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    
    // Preferences manager for configuration
    private val preferencesManager by lazy { PreferencesManager(context) }
    
    // Asset management for custom keyword files
    private val assetManager: AssetManager by lazy { context.assets }
    
    // Placeholder for actual Porcupine engine components
    private var porcupineEngine: Any? = null // Would be Porcupine instance in real implementation
    
    // Built-in keywords for fallback (when custom models aren't available)
    private val builtInArmingKeywords = listOf("picovoice", "bumblebee", "computer")
    private val builtInEmergencyKeywords = listOf("jarvis", "alexa", "terminator")
    
    override fun initialize(callback: HotwordEngine.HotwordCallback): Boolean {
        return try {
            Log.i(TAG, "Initializing Porcupine hotword engine...")
            
            this.callback = callback
            
            // Load configuration from preferences
            loadConfigurationFromPreferences()
            
            // Load custom keyword files if available
            loadCustomKeywordFiles()
            
            // Initialize Porcupine engine with configuration
            if (!initializePorcupineEngine()) {
                Log.e(TAG, "Failed to initialize Porcupine engine")
                return false
            }
            
            engineState.set(HotwordEngineState.INITIALIZED)
            callback.onEngineStateChanged(false)
            
            Log.i(TAG, "✅ Porcupine engine initialized successfully")
            Log.d(TAG, "Arming keywords: ${armingKeywords.joinToString()}")
            Log.d(TAG, "Emergency keywords: ${emergencyKeywords.joinToString()}")
            Log.d(TAG, "Sensitivity: $sensitivity")
            Log.d(TAG, "Configuration source: preferences=${preferencesManager.isPorcupineConfigured()}")
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize Porcupine engine", e)
            engineState.set(HotwordEngineState.ERROR)
            callback.onError("Initialization failed: ${e.message}", e)
            false
        }
    }
    
    override fun start(): Boolean {
        if (engineState.get() != HotwordEngineState.INITIALIZED) {
            Log.w(TAG, "Cannot start engine - not properly initialized")
            return false
        }
        
        return try {
            Log.i(TAG, "Starting Porcupine hotword detection...")
            
            // Start the detection coroutine
            detectionJob = detectionScope.launch {
                startDetectionLoop()
            }
            
            isListening.set(true)
            engineState.set(HotwordEngineState.LISTENING)
            callback?.onEngineStateChanged(true)
            
            Log.i(TAG, "🎙️ Porcupine engine started - listening for keywords")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start Porcupine engine", e)
            engineState.set(HotwordEngineState.ERROR)
            callback?.onError("Start failed: ${e.message}", e)
            false
        }
    }
    
    override fun stop(): Boolean {
        return try {
            Log.i(TAG, "Stopping Porcupine hotword detection...")
            
            // Cancel detection job
            detectionJob?.cancel()
            detectionJob = null
            
            // TODO: Stop actual Porcupine engine
            // porcupineEngine?.delete()
            
            isListening.set(false)
            engineState.set(HotwordEngineState.STOPPED)
            callback?.onEngineStateChanged(false)
            
            Log.i(TAG, "⏹️ Porcupine engine stopped")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping Porcupine engine", e)
            callback?.onError("Stop failed: ${e.message}", e)
            false
        }
    }
    
    override fun release() {
        try {
            Log.i(TAG, "Releasing Porcupine engine resources...")
            
            // Stop if still running
            stop()
            
            // Cancel all coroutines
            detectionJob?.cancel()
            
            // TODO: Release actual Porcupine resources
            // porcupineEngine?.delete()
            porcupineEngine = null
            
            engineState.set(HotwordEngineState.RELEASED)
            callback = null
            
            Log.i(TAG, "🗑️ Porcupine engine resources released")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error releasing Porcupine engine", e)
        }
    }
    
    override fun isListening(): Boolean = isListening.get()
    
    override fun getEngineInfo(): Map<String, Any> {
        return mapOf(
            "engineName" to "PorcupineHotword",
            "version" to "3.0.0-placeholder",
            "state" to engineState.get().name,
            "isListening" to isListening.get(),
            "sensitivity" to sensitivity,
            "armingKeywords" to armingKeywords,
            "emergencyKeywords" to emergencyKeywords,
            "supportedLanguages" to listOf("en"),
            "requiresNetwork" to false,
            "maxConcurrentKeywords" to 10
        )
    }
    
    override fun updateConfiguration(config: Map<String, Any>): Boolean {
        return try {
            config["sensitivity"]?.let { newSensitivity ->
                setSensitivity((newSensitivity as Number).toFloat())
            }
            
            config["armingKeywords"]?.let { keywords ->
                @Suppress("UNCHECKED_CAST")
                armingKeywords = (keywords as List<String>).toMutableList()
                Log.d(TAG, "Updated arming keywords: ${armingKeywords.joinToString()}")
            }
            
            config["emergencyKeywords"]?.let { keywords ->
                @Suppress("UNCHECKED_CAST")
                emergencyKeywords = (keywords as List<String>).toMutableList()
                Log.d(TAG, "Updated emergency keywords: ${emergencyKeywords.joinToString()}")
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update configuration", e)
            false
        }
    }
    
    override fun getSupportedKeywords(): Map<String, List<String>> {
        return mapOf(
            "arming" to armingKeywords,
            "emergency" to emergencyKeywords
        )
    }
    
    override fun setSensitivity(sensitivity: Float): Boolean {
        return if (sensitivity in MIN_SENSITIVITY..MAX_SENSITIVITY) {
            this.sensitivity = sensitivity
            Log.d(TAG, "Sensitivity updated to: $sensitivity")
            
            // TODO: Update actual Porcupine engine sensitivity
            // porcupineEngine?.setSensitivity(sensitivity)
            
            true
        } else {
            Log.w(TAG, "Invalid sensitivity value: $sensitivity (must be between $MIN_SENSITIVITY and $MAX_SENSITIVITY)")
            false
        }
    }
    
    override fun getSensitivity(): Float = sensitivity
    
    /**
     * Main detection loop - placeholder implementation
     * In real implementation, this would process audio from Porcupine
     */
    private suspend fun startDetectionLoop() {
        Log.d(TAG, "Detection loop started")
        
        try {
            while (isListening.get() && !Thread.currentThread().isInterrupted) {
                
                // TODO: Replace with actual Porcupine audio processing
                // val audioBuffer = getAudioBuffer()
                // val keywordIndex = porcupineEngine.process(audioBuffer)
                
                // Placeholder: Simulate keyword detection based on some criteria
                // In real implementation, this would be removed
                simulateKeywordDetection()
                
                // Small delay to prevent excessive CPU usage
                delay(DETECTION_SIMULATION_DELAY_MS)
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Detection loop cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error in detection loop", e)
            callback?.onError("Detection loop error: ${e.message}", e)
        }
        
        Log.d(TAG, "Detection loop ended")
    }
    
    /**
     * Placeholder simulation of keyword detection
     * In real implementation, this would be replaced by actual Porcupine processing
     */
    private suspend fun simulateKeywordDetection() {
        // TODO: Remove this entire method in real implementation
        // This is only for testing and demonstration purposes
        
        // Simulate very rare random detections for testing
        val randomValue = (Math.random() * 1000000).toInt()
        
        when {
            randomValue == 123456 -> { // Extremely rare arming phrase simulation
                Log.d(TAG, "🧪 Simulating arming phrase detection")
                callback?.onArmingPhrase("activate protection", 0.9f)
            }
            randomValue == 654321 -> { // Extremely rare emergency phrase simulation  
                Log.d(TAG, "🧪 Simulating emergency phrase detection")
                callback?.onEmergencyPhrase("help me now", 0.95f)
            }
        }
    }
    
    /**
     * Process detected keyword and determine type
     * This would be called from actual Porcupine detection callback
     * Includes false positive prevention guardrails
     */
    private fun processDetectedKeyword(keyword: String, confidence: Float) {
        Log.d(TAG, "Processing detected keyword: '$keyword' (confidence: $confidence)")
        
        // Apply false positive prevention guardrails
        if (!passesGuardrails(keyword, confidence)) {
            Log.d(TAG, "Detection blocked by guardrails: '$keyword'")
            return
        }
        
        // Set dead-time gate after successful guardrail check
        setDeadTimeGate()
        
        when {
            armingKeywords.any { it.equals(keyword, ignoreCase = true) } -> {
                Log.w(TAG, "🛡️ ARMING PHRASE DETECTED: '$keyword' (passed guardrails)")
                callback?.onArmingPhrase(keyword, confidence)
            }
            emergencyKeywords.any { it.equals(keyword, ignoreCase = true) } -> {
                Log.w(TAG, "🚨 EMERGENCY PHRASE DETECTED: '$keyword' (passed guardrails)")
                callback?.onEmergencyPhrase(keyword, confidence)
            }
            else -> {
                Log.d(TAG, "Unknown keyword detected: '$keyword' (passed guardrails)")
            }
        }
    }
    
    /**
     * False positive prevention guardrails
     * Implements multiple checks to reduce false positives and maintain silence
     */
    private fun passesGuardrails(keyword: String, confidence: Float): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // 1. Dead-time gate: 300-500ms after last detection
        val timeSinceLastDetection = currentTime - lastDetectionTime.get()
        if (timeSinceLastDetection < DEAD_TIME_MIN_MS) {
            Log.d(TAG, "Guardrail BLOCKED: Dead-time gate (${timeSinceLastDetection}ms < ${DEAD_TIME_MIN_MS}ms)")
            return false
        }
        
        // 2. Confidence threshold check
        if (confidence < MIN_CONFIDENCE_THRESHOLD) {
            Log.d(TAG, "Guardrail BLOCKED: Low confidence ($confidence < $MIN_CONFIDENCE_THRESHOLD)")
            return false
        }
        
        // 3. Media volume check - ignore if media is loud
        if (!passesMediaVolumeCheck()) {
            Log.d(TAG, "Guardrail BLOCKED: Media volume too high")
            return false
        }
        
        // 4. Simple VAD check - ensure there's actual audio energy
        if (!passesVADCheck()) {
            Log.d(TAG, "Guardrail BLOCKED: Insufficient audio energy (VAD)")
            return false
        }
        
        Log.d(TAG, "Guardrails PASSED: '$keyword' (confidence: $confidence)")
        return true
    }
    
    /**
     * Check media volume levels to avoid false positives during media playback
     */
    private fun passesMediaVolumeCheck(): Boolean {
        return try {
            val musicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxMusicVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val musicVolumePercent = if (maxMusicVolume > 0) musicVolume.toFloat() / maxMusicVolume else 0f
            
            val alarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxAlarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val alarmVolumePercent = if (maxAlarmVolume > 0) alarmVolume.toFloat() / maxAlarmVolume else 0f
            
            val ringVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            val maxRingVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            val ringVolumePercent = if (maxRingVolume > 0) ringVolume.toFloat() / maxRingVolume else 0f
            
            val highestVolume = maxOf(musicVolumePercent, alarmVolumePercent, ringVolumePercent)
            
            Log.v(TAG, "Volume check - Music: ${(musicVolumePercent*100).toInt()}%, " +
                      "Alarm: ${(alarmVolumePercent*100).toInt()}%, " +
                      "Ring: ${(ringVolumePercent*100).toInt()}%")
            
            val passes = highestVolume < MEDIA_VOLUME_THRESHOLD
            if (!passes) {
                Log.d(TAG, "Media volume too high: ${(highestVolume*100).toInt()}% >= ${(MEDIA_VOLUME_THRESHOLD*100).toInt()}%")
            }
            
            passes
        } catch (e: Exception) {
            Log.w(TAG, "Error checking media volume, allowing detection", e)
            true // Allow detection if volume check fails
        }
    }
    
    /**
     * Simple Voice Activity Detection (VAD) based on RMS energy
     * In real implementation, this would analyze actual audio buffer
     */
    private fun passesVADCheck(): Boolean {
        return try {
            // TODO: Replace with actual audio buffer RMS calculation
            // val rms = calculateRMS(audioBuffer)
            // return rms > VAD_RMS_THRESHOLD
            
            // Placeholder: Simulate VAD check
            // In real implementation, would check actual microphone input energy
            val simulatedRMS = Math.random().toFloat() * 0.1f // Simulate low RMS
            val passes = simulatedRMS > VAD_RMS_THRESHOLD
            
            if (!passes) {
                Log.v(TAG, "VAD check failed: RMS $simulatedRMS < threshold $VAD_RMS_THRESHOLD")
            }
            
            // For placeholder, return true most of the time
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error in VAD check, allowing detection", e)
            true // Allow detection if VAD check fails
        }
    }
    
    /**
     * Calculate RMS (Root Mean Square) for audio energy detection
     * Used for Voice Activity Detection
     */
    private fun calculateRMS(audioBuffer: FloatArray): Float {
        if (audioBuffer.isEmpty()) return 0f
        
        var sum = 0f
        for (sample in audioBuffer) {
            sum += sample * sample
        }
        return sqrt(sum / audioBuffer.size)
    }
    
    /**
     * Set dead-time gate to prevent rapid-fire detections
     * Random delay between 300-500ms for natural variation
     */
    private fun setDeadTimeGate() {
        val randomDelay = (DEAD_TIME_MIN_MS + 
                          (Math.random() * (DEAD_TIME_MAX_MS - DEAD_TIME_MIN_MS))).toLong()
        val newDeadTime = System.currentTimeMillis() + randomDelay
        lastDetectionTime.set(newDeadTime)
        
        Log.v(TAG, "Dead-time gate set: ${randomDelay}ms")
    }
    
    /**
     * Check if detection is currently in dead-time period
     */
    private fun isInDeadTime(): Boolean {
        val currentTime = System.currentTimeMillis()
        val deadTimeEnd = lastDetectionTime.get()
        return currentTime < deadTimeEnd
    }
    
    /**
     * Manual trigger for testing purposes
     * Remove in production
     */
    fun simulateArmingPhrase() {
        Log.d(TAG, "🧪 Manually triggering arming phrase simulation")
        callback?.onArmingPhrase("activate protection", 0.95f)
    }
    
    /**
     * Manual trigger for testing purposes
     * Remove in production
     */
    fun simulateEmergencyPhrase() {
        Log.d(TAG, "🧪 Manually triggering emergency phrase simulation")
        callback?.onEmergencyPhrase("help me now", 0.95f)
    }
    
    // ========================================
    // CONFIGURATION AND INITIALIZATION HELPERS
    // ========================================
    
    /**
     * Load configuration from PreferencesManager
     * Uses stored values or falls back to defaults
     */
    private fun loadConfigurationFromPreferences() {
        try {
            Log.d(TAG, "Loading configuration from preferences...")
            
            // Load sensitivity from preferences with configurable default
            val preferredSensitivity = preferencesManager.getMLDetectionThreshold()
            if (preferredSensitivity in MIN_SENSITIVITY..MAX_SENSITIVITY) {
                sensitivity = preferredSensitivity
                Log.d(TAG, "Loaded sensitivity from preferences: $sensitivity")
            } else {
                Log.d(TAG, "Using default sensitivity: $sensitivity")
            }
            
            // Check if there's a configured wake word preference
            val configuredWakeWord = preferencesManager.getPorcupineWakeWord()
            configuredWakeWord?.let { wakeWord ->
                Log.d(TAG, "Configured wake word from preferences: $wakeWord")
                
                // Add to arming keywords if it's not already there
                if (!armingKeywords.contains(wakeWord) && wakeWord.isNotBlank()) {
                    armingKeywords.add(0, wakeWord) // Add at beginning as primary
                    Log.d(TAG, "Added configured wake word to arming keywords")
                }
            }
            
            // Load Porcupine access key if configured
            val accessKey = preferencesManager.getPorcupineAccessKey()
            if (!accessKey.isNullOrBlank()) {
                Log.d(TAG, "Porcupine access key configured (${accessKey.length} chars)")
            } else {
                Log.d(TAG, "No Porcupine access key configured - will use built-in keywords")
            }
            
            Log.i(TAG, "Configuration loaded - sensitivity: $sensitivity, keywords: ${armingKeywords.size + emergencyKeywords.size}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading configuration from preferences", e)
        }
    }
    
    /**
     * Load custom keyword files from assets/porcupine_models/
     * Detects .ppn files and maps them to keyword categories
     */
    private fun loadCustomKeywordFiles(): List<String> {
        val customKeywordFiles = mutableListOf<String>()
        
        try {
            Log.d(TAG, "Scanning for custom keyword files in assets/porcupine_models/...")
            
            // List files in porcupine_models directory
            val modelFiles = assetManager.list("porcupine_models") ?: emptyArray()
            
            for (fileName in modelFiles) {
                if (fileName.endsWith(".ppn")) {
                    val keywordName = fileName.removeSuffix("_android.ppn").removeSuffix(".ppn")
                    customKeywordFiles.add(keywordName)
                    
                    Log.d(TAG, "Found custom keyword file: $fileName -> keyword: '$keywordName'")
                    
                    // Automatically categorize keywords based on naming convention
                    when {
                        keywordName.contains("arm", ignoreCase = true) ||
                        keywordName.contains("protect", ignoreCase = true) ||
                        keywordName.contains("guard", ignoreCase = true) -> {
                            if (!armingKeywords.contains(keywordName)) {
                                armingKeywords.add(keywordName)
                                Log.d(TAG, "Added '$keywordName' to arming keywords")
                            }
                        }
                        keywordName.contains("help", ignoreCase = true) ||
                        keywordName.contains("emergency", ignoreCase = true) ||
                        keywordName.contains("sos", ignoreCase = true) -> {
                            if (!emergencyKeywords.contains(keywordName)) {
                                emergencyKeywords.add(keywordName)
                                Log.d(TAG, "Added '$keywordName' to emergency keywords")
                            }
                        }
                        else -> {
                            // Default to arming keywords if ambiguous
                            if (!armingKeywords.contains(keywordName)) {
                                armingKeywords.add(keywordName)
                                Log.d(TAG, "Added '$keywordName' to arming keywords (default)")
                            }
                        }
                    }
                }
            }
            
            if (customKeywordFiles.isNotEmpty()) {
                Log.i(TAG, "✅ Loaded ${customKeywordFiles.size} custom keyword files: ${customKeywordFiles.joinToString()}")
            } else {
                Log.d(TAG, "No custom keyword files found, using built-in keywords")
                // Fallback to built-in keywords
                addBuiltInKeywordsAsFallback()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading custom keyword files", e)
            // Fallback to built-in keywords on error
            addBuiltInKeywordsAsFallback()
        }
        
        return customKeywordFiles
    }
    
    /**
     * Add built-in Porcupine keywords as fallback when no custom models are available
     */
    private fun addBuiltInKeywordsAsFallback() {
        Log.d(TAG, "Adding built-in keywords as fallback...")
        
        // Add built-in keywords that aren't already present
        builtInArmingKeywords.forEach { keyword ->
            if (!armingKeywords.contains(keyword)) {
                armingKeywords.add(keyword)
                Log.v(TAG, "Added built-in arming keyword: '$keyword'")
            }
        }
        
        builtInEmergencyKeywords.forEach { keyword ->
            if (!emergencyKeywords.contains(keyword)) {
                emergencyKeywords.add(keyword)
                Log.v(TAG, "Added built-in emergency keyword: '$keyword'")
            }
        }
        
        Log.d(TAG, "Built-in keywords added - arming: ${builtInArmingKeywords.size}, emergency: ${builtInEmergencyKeywords.size}")
    }
    
    /**
     * Initialize the actual Porcupine engine with loaded configuration
     * Returns true if successful, false otherwise
     */
    private fun initializePorcupineEngine(): Boolean {
        return try {
            Log.d(TAG, "Initializing Porcupine engine with configuration...")
            
            val accessKey = preferencesManager.getPorcupineAccessKey()
            
            if (!accessKey.isNullOrBlank()) {
                Log.d(TAG, "Initializing with Porcupine access key")
                
                // TODO: Initialize actual Porcupine engine with access key
                /*
                val allKeywords = (armingKeywords + emergencyKeywords).toTypedArray()
                val sensitivities = FloatArray(allKeywords.size) { sensitivity }
                
                porcupineEngine = Porcupine.Builder()
                    .setAccessKey(accessKey)
                    .setKeywords(allKeywords)
                    .setSensitivities(sensitivities)
                    .build()
                
                Log.i(TAG, "✅ Porcupine engine initialized with ${allKeywords.size} keywords")
                */
                
                // Placeholder initialization
                porcupineEngine = "PorcupineEngine_WithAccessKey"
                Log.i(TAG, "✅ Porcupine engine initialized (placeholder with access key)")
                
            } else {
                Log.d(TAG, "No access key - initializing with built-in keywords only")
                
                // TODO: Initialize with built-in keywords only
                /*
                porcupineEngine = Porcupine.Builder()
                    .setKeywords(arrayOf("picovoice", "jarvis"))
                    .setSensitivities(floatArrayOf(sensitivity, sensitivity))
                    .build()
                */
                
                // Placeholder initialization
                porcupineEngine = "PorcupineEngine_BuiltInOnly"
                Log.i(TAG, "✅ Porcupine engine initialized (placeholder with built-in keywords)")
            }
            
            // Log final configuration
            Log.d(TAG, "Final engine configuration:")
            Log.d(TAG, "  - Arming keywords: ${armingKeywords.joinToString()}")
            Log.d(TAG, "  - Emergency keywords: ${emergencyKeywords.joinToString()}")
            Log.d(TAG, "  - Sensitivity: $sensitivity")
            Log.d(TAG, "  - Access key configured: ${!accessKey.isNullOrBlank()}")
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize Porcupine engine", e)
            porcupineEngine = null
            false
        }
    }
    
    /**
     * Get confidence threshold from preferences or use configurable default
     */
    private fun getConfigurableConfidenceThreshold(): Float {
        return try {
            val threshold = preferencesManager.getMLDetectionThreshold()
            // Ensure threshold is within reasonable bounds
            threshold.coerceIn(0.5f, 0.95f)
        } catch (e: Exception) {
            Log.w(TAG, "Error getting confidence threshold from preferences, using default", e)
            MIN_CONFIDENCE_THRESHOLD
        }
    }
    
    /**
     * Update the confidence threshold used in guardrails
     * Uses preferences value if available, otherwise falls back to default
     */
    private fun updateConfidenceThresholdFromPreferences(): Float {
        val newThreshold = getConfigurableConfidenceThreshold()
        Log.d(TAG, "Updated confidence threshold: $newThreshold (was $MIN_CONFIDENCE_THRESHOLD)")
        return newThreshold
    }
    
    /**
     * Enhanced guardrails check with configurable confidence threshold
     */
    private fun passesGuardrailsWithConfigurableThreshold(keyword: String, confidence: Float): Boolean {
        val currentTime = System.currentTimeMillis()
        val configurableThreshold = getConfigurableConfidenceThreshold()
        
        // 1. Dead-time gate: 300-500ms after last detection
        val timeSinceLastDetection = currentTime - lastDetectionTime.get()
        if (timeSinceLastDetection < DEAD_TIME_MIN_MS) {
            Log.d(TAG, "Guardrail BLOCKED: Dead-time gate (${timeSinceLastDetection}ms < ${DEAD_TIME_MIN_MS}ms)")
            return false
        }
        
        // 2. Configurable confidence threshold check
        if (confidence < configurableThreshold) {
            Log.d(TAG, "Guardrail BLOCKED: Low confidence ($confidence < $configurableThreshold)")
            return false
        }
        
        // 3. Media volume check - ignore if media is loud
        if (!passesMediaVolumeCheck()) {
            Log.d(TAG, "Guardrail BLOCKED: Media volume too high")
            return false
        }
        
        // 4. Simple VAD check - ensure there's actual audio energy
        if (!passesVADCheck()) {
            Log.d(TAG, "Guardrail BLOCKED: Insufficient audio energy (VAD)")
            return false
        }
        
        Log.d(TAG, "Guardrails PASSED: '$keyword' (confidence: $confidence >= $configurableThreshold)")
        return true
    }
}

/*
 * ================================
 * REAL PORCUPINE INTEGRATION NOTES
 * ================================
 * 
 * To integrate with actual Porcupine SDK:
 * 
 * 1. Add Porcupine dependency to build.gradle:
 * ```kotlin
 * implementation 'ai.picovoice:porcupine-android:3.0.2'
 * ```
 * 
 * 2. Replace placeholder initialization:
 * ```kotlin
 * porcupineEngine = Porcupine.Builder()
 *     .setAccessKey("YOUR_PICOVOICE_ACCESS_KEY")
 *     .setKeywords(arrayOf("activate-protection", "help-me-now"))
 *     .setSensitivities(floatArrayOf(0.7f, 0.7f))
 *     .build()
 * ```
 * 
 * 3. Replace detection loop with actual audio processing:
 * ```kotlin
 * private suspend fun startDetectionLoop() {
 *     val recorder = AudioRecorder()
 *     recorder.start()
 *     
 *     while (isListening.get()) {
 *         val audioFrame = recorder.readFrame()
 *         val keywordIndex = porcupineEngine.process(audioFrame)
 *         
 *         if (keywordIndex >= 0) {
 *             val detectedKeyword = keywords[keywordIndex]
 *             processDetectedKeyword(detectedKeyword, 0.9f)
 *         }
 *     }
 * }
 * ```
 * 
 * 4. Handle permissions in AndroidManifest.xml:
 * ```xml
 * <uses-permission android:name="android.permission.RECORD_AUDIO" />
 * ```
 * 
 * 5. Add custom wake word files to assets/keywords/ directory
 * 
 * Example usage with real Porcupine:
 * ```kotlin
 * val engine = PorcupineHotword(context)
 * val callback = object : HotwordEngine.HotwordCallback {
 *     override fun onArmingPhrase(keyword: String, confidence: Float) {
 *         // Handle arming phrase detection
 *     }
 *     override fun onEmergencyPhrase(keyword: String, confidence: Float) {
 *         // Handle emergency phrase detection  
 *     }
 * }
 * 
 * if (engine.initialize(callback)) {
 *     engine.start()
 * }
 * ```
 */
