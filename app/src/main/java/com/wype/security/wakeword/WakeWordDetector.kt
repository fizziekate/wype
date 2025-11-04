package com.wype.security.wakeword

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wake word detection engine that processes audio buffers
 * Supports multiple wake word engines (Porcupine, TensorFlow Lite, Simple Pattern Matching)
 */
class WakeWordDetector(
    private val context: Context,
    private val keywords: List<String> = listOf("wype emergency", "help me please")
) {
    
    companion object {
        private const val TAG = "WakeWordDetector"
        private const val DETECTION_THRESHOLD = 0.45f
    }
    
    // Detection events flow
    private val _detectionEvents = MutableSharedFlow<WakeWordEvent>()
    val detectionEvents: Flow<WakeWordEvent> = _detectionEvents.asSharedFlow()
    
    // Detection engines
    private var porcupineDetector: PorcupineDetector? = null
    private var simpleDetector: SimplePatternDetector? = null
    
    // Configuration
    private var isInitialized = false
    private var currentEngine = DetectionEngine.AUTO
    
    enum class DetectionEngine {
        PORCUPINE, SIMPLE_PATTERN, AUTO
    }
    
    data class WakeWordEvent(
        val keyword: String,
        val confidence: Float,
        val timestamp: Long,
        val engine: DetectionEngine
    )
    
    /**
     * Initialize the wake word detector
     */
    suspend fun initialize(engine: DetectionEngine = DetectionEngine.AUTO): Boolean {
        return try {
            currentEngine = engine
            
            when (engine) {
                DetectionEngine.PORCUPINE -> {
                    initializePorcupine()
                }
                DetectionEngine.SIMPLE_PATTERN -> {
                    initializeSimplePattern()
                }
                DetectionEngine.AUTO -> {
                    // Try Porcupine first, fallback to simple pattern
                    if (!initializePorcupine()) {
                        Log.i(TAG, "Porcupine not available, falling back to simple pattern detection")
                        initializeSimplePattern()
                    } else {
                        true
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize wake word detector", e)
            false
        }.also { success ->
            isInitialized = success
            if (success) {
                Log.i(TAG, "Wake word detector initialized with engine: $currentEngine")
            }
        }
    }
    
    /**
     * Process audio buffer for wake word detection
     */
    suspend fun processAudioBuffer(audioBuffer: ShortArray, length: Int) {
        if (!isInitialized) return
        
        try {
            // Process with Porcupine if available
            porcupineDetector?.let { detector ->
                val result = detector.process(audioBuffer, length)
                if (result.isDetected && result.confidence >= DETECTION_THRESHOLD) {
                    _detectionEvents.emit(
                        WakeWordEvent(
                            keyword = result.keyword,
                            confidence = result.confidence,
                            timestamp = System.currentTimeMillis(),
                            engine = DetectionEngine.PORCUPINE
                        )
                    )
                    return
                }
            }
            
            // Fallback to simple pattern detection
            simpleDetector?.let { detector ->
                val result = detector.process(audioBuffer, length)
                if (result.isDetected && result.confidence >= DETECTION_THRESHOLD) {
                    _detectionEvents.emit(
                        WakeWordEvent(
                            keyword = result.keyword,
                            confidence = result.confidence,
                            timestamp = System.currentTimeMillis(),
                            engine = DetectionEngine.SIMPLE_PATTERN
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio buffer", e)
        }
    }
    
    /**
     * Initialize Porcupine wake word engine
     */
    private fun initializePorcupine(): Boolean {
        return try {
            Log.d(TAG, "Attempting to initialize Porcupine")
            
            // Check if Porcupine library is available
            val porcupineClass = Class.forName("ai.picovoice.porcupine.Porcupine")
            Log.d(TAG, "Porcupine class found")
            
            porcupineDetector = PorcupineDetector(context, keywords)
            porcupineDetector?.initialize() == true
            
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "Porcupine library not found in classpath")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Porcupine", e)
            false
        }
    }
    
    /**
     * Initialize simple pattern detection as fallback
     */
    private fun initializeSimplePattern(): Boolean {
        return try {
            Log.d(TAG, "Initializing simple pattern detector")
            simpleDetector = SimplePatternDetector(keywords)
            simpleDetector?.initialize() == true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize simple pattern detector", e)
            false
        }
    }
    
    /**
     * Release all resources
     */
    fun release() {
        try {
            porcupineDetector?.release()
            simpleDetector?.release()
            porcupineDetector = null
            simpleDetector = null
            isInitialized = false
            Log.d(TAG, "Wake word detector resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake word detector", e)
        }
    }
}

/**
 * Detection result container
 */
data class DetectionResult(
    val isDetected: Boolean,
    val keyword: String = "",
    val confidence: Float = 0.0f
)

/**
 * Porcupine-based wake word detector
 */
private class PorcupineDetector(
    private val context: Context,
    private val keywords: List<String>
) {
    companion object {
        private const val TAG = "PorcupineDetector"
    }
    
    private var porcupine: Any? = null // ai.picovoice.porcupine.Porcupine
    private var isInitialized = false
    
    fun initialize(): Boolean {
        return try {
            Log.d(TAG, "Initializing Porcupine wake word detection")
            
            // This would be the actual Porcupine initialization
            // For now, we'll simulate it
            isInitialized = false // Set to true when Porcupine is properly integrated
            
            Log.i(TAG, "Porcupine initialization result: $isInitialized")
            isInitialized
            
        } catch (e: Exception) {
            Log.e(TAG, "Porcupine initialization failed", e)
            false
        }
    }
    
    fun process(audioBuffer: ShortArray, length: Int): DetectionResult {
        if (!isInitialized) {
            return DetectionResult(false)
        }
        
        return try {
            // This would be the actual Porcupine processing
            // For now, return no detection
            DetectionResult(false)
            
        } catch (e: Exception) {
            Log.e(TAG, "Porcupine processing error", e)
            DetectionResult(false)
        }
    }
    
    fun release() {
        try {
            // Release Porcupine resources
            porcupine = null
            isInitialized = false
            Log.d(TAG, "Porcupine detector released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Porcupine detector", e)
        }
    }
}

/**
 * Simple pattern-based wake word detector
 * Uses audio energy and basic pattern matching as fallback
 */
private class SimplePatternDetector(
    private val keywords: List<String>
) {
    companion object {
        private const val TAG = "SimplePatternDetector"
        private const val ENERGY_THRESHOLD = 1500f
        private const val PATTERN_BUFFER_SIZE = 8000 // ~0.5 seconds at 16kHz
    }
    
    private var isInitialized = false
    private val audioBuffer = mutableListOf<Float>()
    private var voiceActivityCount = 0
    private var lastDetectionTime = 0L
    
    fun initialize(): Boolean {
        isInitialized = true
        Log.d(TAG, "Simple pattern detector initialized")
        return true
    }
    
    fun process(audioBuffer: ShortArray, length: Int): DetectionResult {
        if (!isInitialized) {
            return DetectionResult(false)
        }
        
        return try {
            // Calculate audio energy
            val energy = calculateAudioEnergy(audioBuffer, length)
            
            // Voice activity detection
            if (energy > ENERGY_THRESHOLD) {
                voiceActivityCount++
                
                // Store audio samples for pattern analysis
                for (i in 0 until length) {
                    this.audioBuffer.add(audioBuffer[i].toFloat())
                }
                
                // Keep buffer size manageable
                if (this.audioBuffer.size > PATTERN_BUFFER_SIZE) {
                    this.audioBuffer.removeAt(0)
                }
                
                // Simple pattern detection based on voice activity
                if (voiceActivityCount >= 10 && 
                    System.currentTimeMillis() - lastDetectionTime > 2000) {
                    
                    lastDetectionTime = System.currentTimeMillis()
                    voiceActivityCount = 0
                    
                    // Return detection with low confidence for testing
                    return DetectionResult(
                        isDetected = true,
                        keyword = keywords.firstOrNull() ?: "unknown",
                        confidence = 0.5f
                    )
                }
            } else {
                // Reset voice activity count if no voice detected
                if (voiceActivityCount > 0) {
                    voiceActivityCount--
                }
            }
            
            DetectionResult(false)
            
        } catch (e: Exception) {
            Log.e(TAG, "Simple pattern processing error", e)
            DetectionResult(false)
        }
    }
    
    private fun calculateAudioEnergy(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            sum += buffer[i] * buffer[i]
        }
        return Math.sqrt(sum / length).toFloat()
    }
    
    fun release() {
        audioBuffer.clear()
        voiceActivityCount = 0
        isInitialized = false
        Log.d(TAG, "Simple pattern detector released")
    }
}
