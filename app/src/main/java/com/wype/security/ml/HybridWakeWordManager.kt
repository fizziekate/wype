package com.wype.security.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Hybrid wake word detection manager that supports multiple lightweight models
 * Allows switching between Porcupine, TensorFlow Lite, and custom neural networks
 * Optimized for minimal resource usage and maximum flexibility
 */
class HybridWakeWordManager(
    private val context: Context,
    private val callback: WakeWordManagerCallback
) : CoroutineScope {

    companion object {
        private const val TAG = "HybridWakeWordManager"
        
        // Detection modes
        enum class DetectionMode {
            PORCUPINE,        // Picovoice Porcupine (existing)
            TENSORFLOW_LITE,  // TensorFlow Lite models
            SIMPLE_NEURAL,    // Custom lightweight neural network
            TEMPLATE_MATCHING,// DTW comparison against user's saved recording (recommended)
            AUTO              // Automatically choose best option
        }
        
        // Performance profiles
        enum class PerformanceProfile {
            ULTRA_LOW_POWER,  // Minimal CPU usage, basic detection
            BALANCED,         // Good balance of accuracy and efficiency
            HIGH_ACCURACY     // Maximum accuracy, higher resource usage
        }
    }

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.Default + job

    // Detection engines
    private var tfliteDetector: LightweightWakeWordDetector? = null
    private var simpleNeuralDetector: SimpleNeuralWakeWordDetector? = null
    private var templateDetector: TemplateWakeWordDetector? = null
    
    // Current configuration
    private var currentMode = DetectionMode.AUTO
    private var currentProfile = PerformanceProfile.BALANCED
    private var isListening = false
    
    // Model paths and configurations
    private val availableModels = mutableMapOf<String, ModelConfig>()

    interface WakeWordManagerCallback {
        fun onWakeWordDetected(wakeWord: String, confidence: Float, detectorType: String)
        fun onError(error: String, detectorType: String)
        fun onDetectorSwitched(newDetector: String, reason: String)
    }

    data class ModelConfig(
        val name: String,
        val path: String,
        val detectorType: DetectionMode,
        val expectedAccuracy: Float,
        val estimatedCpuUsage: Float,
        val modelSizeBytes: Long
    )

    /**
     * Initialize the hybrid manager
     */
    fun initialize(): Boolean {
        return try {
            // Scan for available models
            scanAvailableModels()
            
            // Initialize detectors based on profile
            initializeDetectors()
            
            Log.i(TAG, "Hybrid wake word manager initialized")
            Log.i(TAG, "Available models: ${availableModels.keys.joinToString()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize hybrid manager", e)
            callback.onError("Failed to initialize: ${e.message}", "HybridManager")
            false
        }
    }

    /**
     * Start wake word detection with the specified mode
     */
    fun startDetection(
        mode: DetectionMode = DetectionMode.AUTO,
        profile: PerformanceProfile = PerformanceProfile.BALANCED,
        wakeWord: String = "wype"
    ): Boolean {
        if (isListening) {
            Log.w(TAG, "Already listening")
            return true
        }

        currentMode = mode
        currentProfile = profile

        return try {
            val selectedDetector = selectOptimalDetector(mode, profile, wakeWord)

            when (selectedDetector) {
                DetectionMode.TEMPLATE_MATCHING -> startTemplateMatchingDetection(wakeWord)
                DetectionMode.TENSORFLOW_LITE   -> startTensorFlowLiteDetection(wakeWord)
                DetectionMode.SIMPLE_NEURAL     -> startSimpleNeuralDetection(wakeWord)
                DetectionMode.PORCUPINE -> {
                    Log.i(TAG, "Falling back to Porcupine detection")
                    callback.onDetectorSwitched("Porcupine", "Selected as optimal detector")
                    true
                }
                DetectionMode.AUTO -> {
                    Log.w(TAG, "AUTO mode reached end of chain, using simple neural")
                    startSimpleNeuralDetection(wakeWord)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start detection", e)
            callback.onError("Failed to start detection: ${e.message}", currentMode.name)
            false
        }
    }

    /**
     * Stop wake word detection
     */
    fun stopDetection() {
        isListening = false

        try {
            tfliteDetector?.stopListening()
            simpleNeuralDetector?.stopListening()
            templateDetector?.stopListening()
            
            Log.i(TAG, "Stopped wake word detection")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping detection", e)
        }
    }

    /**
     * Release all resources
     */
    fun release() {
        stopDetection()
        job.cancel()
        
        try {
            tfliteDetector?.release()
            tfliteDetector = null

            simpleNeuralDetector?.release()
            simpleNeuralDetector = null

            templateDetector?.release()
            templateDetector = null
            
            Log.i(TAG, "Released all detector resources")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        }
    }

    /**
     * Scan for available models
     */
    private fun scanAvailableModels() {
        // Clear existing models
        availableModels.clear()
        
        try {
            // Scan assets for TensorFlow Lite models
            val assetFiles = context.assets.list("ml_models") ?: emptyArray()
            assetFiles.filter { it.endsWith(".tflite") }.forEach { fileName ->
                val name = fileName.removeSuffix(".tflite")
                availableModels[name] = ModelConfig(
                    name = name,
                    path = "assets://ml_models/$fileName",
                    detectorType = DetectionMode.TENSORFLOW_LITE,
                    expectedAccuracy = 0.85f,
                    estimatedCpuUsage = 0.3f,
                    modelSizeBytes = getAssetSize("ml_models/$fileName")
                )
            }
            
            // Add built-in simple neural network
            availableModels["simple_neural"] = ModelConfig(
                name = "simple_neural",
                path = "", // Not file-based
                detectorType = DetectionMode.SIMPLE_NEURAL,
                expectedAccuracy = 0.75f,
                estimatedCpuUsage = 0.2f,
                modelSizeBytes = 5000L // ~5KB for weights
            )
            
            Log.i(TAG, "Found ${availableModels.size} available models")
            
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning models, will use defaults", e)
            
            // Add fallback simple neural
            availableModels["simple_neural"] = ModelConfig(
                name = "simple_neural",
                path = "",
                detectorType = DetectionMode.SIMPLE_NEURAL,
                expectedAccuracy = 0.75f,
                estimatedCpuUsage = 0.2f,
                modelSizeBytes = 5000L
            )
        }
    }

    /**
     * Initialize detection engines
     */
    private fun initializeDetectors() {
        // TensorFlow Lite detector will be initialized when needed
        // Simple neural detector will be initialized when needed
        Log.d(TAG, "Detector initialization deferred until needed")
    }

    /**
     * Select the optimal detector based on requirements
     */
    private fun selectOptimalDetector(
        mode: DetectionMode,
        profile: PerformanceProfile,
        wakeWord: String
    ): DetectionMode {
        if (mode != DetectionMode.AUTO) {
            Log.i(TAG, "Using specified detector mode: $mode")
            return mode
        }

        // AUTO: always prefer template matching when the user has a saved recording
        val templatePath = getTemplateRecordingPath()
        if (templatePath != null) {
            Log.i(TAG, "AUTO selected TEMPLATE_MATCHING – saved recording found at $templatePath")
            return DetectionMode.TEMPLATE_MATCHING
        }

        // No saved recording – fall back to profile-based selection
        return when (profile) {
            PerformanceProfile.ULTRA_LOW_POWER -> {
                Log.i(TAG, "Selected simple neural for ultra-low power")
                DetectionMode.SIMPLE_NEURAL
            }
            PerformanceProfile.BALANCED -> {
                if (availableModels.values.any { it.detectorType == DetectionMode.TENSORFLOW_LITE }) {
                    Log.i(TAG, "Selected TensorFlow Lite for balanced profile")
                    DetectionMode.TENSORFLOW_LITE
                } else {
                    Log.i(TAG, "Selected simple neural for balanced profile (TFLite unavailable)")
                    DetectionMode.SIMPLE_NEURAL
                }
            }
            PerformanceProfile.HIGH_ACCURACY -> {
                Log.i(TAG, "Selected Porcupine for high accuracy")
                DetectionMode.PORCUPINE
            }
        }
    }

    /**
     * Start TensorFlow Lite detection
     */
    private fun startTensorFlowLiteDetection(wakeWord: String): Boolean {
        return try {
            val tfliteModel = availableModels.values.firstOrNull { 
                it.detectorType == DetectionMode.TENSORFLOW_LITE 
            }
            
            if (tfliteModel == null) {
                Log.w(TAG, "No TensorFlow Lite models available")
                return startSimpleNeuralDetection(wakeWord)
            }

            tfliteDetector = LightweightWakeWordDetector(
                context = context,
                modelPath = tfliteModel.path,
                callback = object : LightweightWakeWordDetector.WakeWordCallback {
                    override fun onWakeWordDetected(confidence: Float) {
                        callback.onWakeWordDetected(wakeWord, confidence, "TensorFlow Lite")
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "TensorFlow Lite error: $error")
                        callback.onError(error, "TensorFlow Lite")
                        
                        // Attempt fallback to simple neural
                        launch {
                            Log.i(TAG, "Attempting fallback to simple neural detector")
                            stopDetection()
                            startSimpleNeuralDetection(wakeWord)
                        }
                    }
                }
            )
            
            if (tfliteDetector?.initialize() == true && tfliteDetector?.startListening() == true) {
                isListening = true
                callback.onDetectorSwitched("TensorFlow Lite", "Optimal for current profile")
                Log.i(TAG, "Started TensorFlow Lite detection")
                true
            } else {
                Log.w(TAG, "Failed to start TensorFlow Lite, falling back to simple neural")
                startSimpleNeuralDetection(wakeWord)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting TensorFlow Lite detection", e)
            startSimpleNeuralDetection(wakeWord)
        }
    }

    /**
     * Start template-matching (DTW) detection using the user's saved recording.
     */
    private fun startTemplateMatchingDetection(wakeWord: String): Boolean {
        val templatePath = getTemplateRecordingPath()
        if (templatePath == null) {
            Log.w(TAG, "No template recording found – falling back to simple neural")
            return startSimpleNeuralDetection(wakeWord)
        }

        return try {
            templateDetector = TemplateWakeWordDetector(
                templatePath = templatePath,
                callback = object : TemplateWakeWordDetector.WakeWordCallback {
                    override fun onWakeWordDetected(confidence: Float, wakeWord: String) {
                        callback.onWakeWordDetected(wakeWord, confidence, "Template DTW")
                    }
                    override fun onError(error: String) {
                        Log.e(TAG, "Template DTW error: $error")
                        callback.onError(error, "Template DTW")
                    }
                }
            )

            if (templateDetector?.initialize() == true && templateDetector?.startListening() == true) {
                isListening = true
                callback.onDetectorSwitched("Template DTW", "Matched against user recording")
                Log.i(TAG, "Started template DTW detection from $templatePath")
                true
            } else {
                Log.w(TAG, "Template DTW failed to start – falling back to simple neural")
                templateDetector = null
                startSimpleNeuralDetection(wakeWord)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting template detection", e)
            startSimpleNeuralDetection(wakeWord)
        }
    }

    /** Returns the path of the saved wake-phrase recording, or null if none exists. */
    private fun getTemplateRecordingPath(): String? {
        // Primary location written by RecordFragment (new format, 16 kHz AAC)
        val m4a = java.io.File(context.filesDir, "wake_phrase_recording.m4a")
        Log.w(TAG, "TEMPLATE CHECK m4a: ${m4a.absolutePath} | exists=${m4a.exists()} | size=${m4a.length()}")
        if (m4a.exists() && m4a.length() > 0) return m4a.absolutePath

        // Legacy 3gp location (8 kHz AMR-NB)
        val gp3 = java.io.File(context.filesDir, "wake_phrase_recording.3gp")
        Log.w(TAG, "TEMPLATE CHECK 3gp: ${gp3.absolutePath} | exists=${gp3.exists()} | size=${gp3.length()}")
        if (gp3.exists() && gp3.length() > 0) return gp3.absolutePath

        // Also check for path saved in SharedPreferences by PreferencesManager
        // PreferencesManager uses PREF_NAME = "wype_preferences", key = "wake_phrase_audio"
        return try {
            val prefs = context.getSharedPreferences("wype_preferences", android.content.Context.MODE_PRIVATE)
            val saved = prefs.getString("wake_phrase_audio", null)
            Log.w(TAG, "TEMPLATE CHECK prefs path: '$saved' | fileExists=${if (saved != null) java.io.File(saved).exists() else false}")
            if (!saved.isNullOrEmpty() && java.io.File(saved).exists()) saved else null
        } catch (e: Exception) {
            Log.e(TAG, "Error reading prefs audio path", e)
            null
        }
    }

    /**
     * Start simple neural network detection
     */
    private fun startSimpleNeuralDetection(wakeWord: String): Boolean {
        return try {
            simpleNeuralDetector = SimpleNeuralWakeWordDetector(
                context = context,
                wakeWord = wakeWord,
                callback = object : SimpleNeuralWakeWordDetector.WakeWordCallback {
                    override fun onWakeWordDetected(confidence: Float, detectedWord: String) {
                        callback.onWakeWordDetected(detectedWord, confidence, "Simple Neural")
                    }

                    override fun onError(error: String) {
                        callback.onError(error, "Simple Neural")
                    }
                }
            )
            
            if (simpleNeuralDetector?.initialize() == true && simpleNeuralDetector?.startListening() == true) {
                isListening = true
                callback.onDetectorSwitched("Simple Neural", "Ultra-lightweight option")
                Log.i(TAG, "Started simple neural detection")
                true
            } else {
                Log.e(TAG, "Failed to start simple neural detection")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting simple neural detection", e)
            false
        }
    }

    /**
     * Get asset file size
     */
    private fun getAssetSize(assetPath: String): Long {
        return try {
            context.assets.openFd(assetPath).use { it.length }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Get current detector information
     */
    fun getCurrentDetectorInfo(): String {
        return when {
            templateDetector    != null && isListening ->
                templateDetector?.getModelInfo()    ?: "Template DTW (unknown)"
            tfliteDetector      != null && isListening ->
                tfliteDetector?.getModelInfo()      ?: "TensorFlow Lite (unknown)"
            simpleNeuralDetector != null && isListening ->
                simpleNeuralDetector?.getModelInfo() ?: "Simple Neural (unknown)"
            else -> "No active detector"
        }
    }

    /**
     * Get performance statistics
     */
    fun getPerformanceStats(): Map<String, Any> {
        val currentModel = availableModels.values.firstOrNull()
        return mapOf(
            "current_detector" to (if (isListening) currentMode.name else "NONE"),
            "performance_profile" to currentProfile.name,
            "is_listening" to isListening,
            "available_models" to availableModels.size,
            "estimated_cpu_usage" to (currentModel?.estimatedCpuUsage ?: 0f),
            "model_size_bytes" to (currentModel?.modelSizeBytes ?: 0L)
        )
    }

    /**
     * Update performance profile dynamically
     */
    fun updatePerformanceProfile(newProfile: PerformanceProfile) {
        if (newProfile != currentProfile) {
            Log.i(TAG, "Switching performance profile from $currentProfile to $newProfile")
            
            val wasListening = isListening
            val currentWakeWord = "wype" // You might want to store this
            
            if (wasListening) {
                stopDetection()
                startDetection(DetectionMode.AUTO, newProfile, currentWakeWord)
            }
            
            currentProfile = newProfile
        }
    }

    /**
     * Test all available detectors
     */
    fun testAllDetectors(): Map<String, String> {
        val results = mutableMapOf<String, String>()
        
        try {
            // Test TensorFlow Lite
            availableModels.values.firstOrNull { it.detectorType == DetectionMode.TENSORFLOW_LITE }?.let { model ->
                try {
                    val testDetector = LightweightWakeWordDetector(
                        context, model.path, object : LightweightWakeWordDetector.WakeWordCallback {
                            override fun onWakeWordDetected(confidence: Float) {}
                            override fun onError(error: String) {}
                        }
                    )
                    if (testDetector.initialize()) {
                        val testResult = testDetector.testDetector()
                        results["TensorFlow Lite"] = "OK (test output: $testResult)"
                        testDetector.release()
                    } else {
                        results["TensorFlow Lite"] = "Failed to initialize"
                    }
                } catch (e: Exception) {
                    results["TensorFlow Lite"] = "Error: ${e.message}"
                }
            }
            
            // Test Simple Neural
            try {
                val testDetector = SimpleNeuralWakeWordDetector(
                    context, "test", object : SimpleNeuralWakeWordDetector.WakeWordCallback {
                        override fun onWakeWordDetected(confidence: Float, wakeWord: String) {}
                        override fun onError(error: String) {}
                    }
                )
                if (testDetector.initialize()) {
                    val testResult = testDetector.testDetector()
                    results["Simple Neural"] = "OK (test output: $testResult)"
                    testDetector.release()
                } else {
                    results["Simple Neural"] = "Failed to initialize"
                }
            } catch (e: Exception) {
                results["Simple Neural"] = "Error: ${e.message}"
            }
            
        } catch (e: Exception) {
            results["Test Error"] = e.message ?: "Unknown error"
        }
        
        return results
    }
}
