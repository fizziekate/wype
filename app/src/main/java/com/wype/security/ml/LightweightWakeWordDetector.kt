package com.wype.security.ml

import android.content.Context
import android.media.AudioRecord
import android.media.AudioFormat
import android.media.AudioAttributes
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.coroutines.CoroutineContext

/**
 * Lightweight wake word detector using TensorFlow Lite
 * Alternative to Porcupine with smaller model size and efficient processing
 */
class LightweightWakeWordDetector(
    private val context: Context,
    private val modelPath: String,
    private val callback: WakeWordCallback
) : CoroutineScope {

    companion object {
        private const val TAG = "LightweightWakeWordDetector"
        
        // Audio configuration
        private const val SAMPLE_RATE = 16000
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        
        // Detection parameters
        private const val DETECTION_THRESHOLD = 0.7f
        private const val COOLDOWN_MS = 2000L
        
        // Buffer sizes
        private const val BUFFER_SIZE_FACTOR = 2
        private val AUDIO_BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        ) * BUFFER_SIZE_FACTOR
        
        private const val PROCESSING_BUFFER_SIZE = SAMPLE_RATE // 1 second of audio
    }

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.Default + job

    private var audioRecord: AudioRecord? = null
    private var tfliteInterpreter: Interpreter? = null
    private val featureExtractor = AudioFeatureExtractor()
    
    private var isListening = false
    private var lastDetectionTime = 0L
    
    // Audio processing buffers
    private val audioBuffer = ShortArray(AUDIO_BUFFER_SIZE)
    private val processingBuffer = FloatArray(PROCESSING_BUFFER_SIZE)
    private var bufferIndex = 0

    interface WakeWordCallback {
        fun onWakeWordDetected(confidence: Float)
        fun onError(error: String)
    }

    /**
     * Initialize the detector
     */
    fun initialize(): Boolean {
        return try {
            // Load TensorFlow Lite model
            loadTFLiteModel()
            
            // Initialize audio recording
            initializeAudioRecord()
            
            Log.i(TAG, "Lightweight wake word detector initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize detector", e)
            callback.onError("Failed to initialize detector: ${e.message}")
            false
        }
    }

    /**
     * Start wake word detection
     */
    fun startListening(): Boolean {
        if (isListening) {
            Log.w(TAG, "Already listening")
            return true
        }

        return try {
            audioRecord?.let { record ->
                record.startRecording()
                isListening = true
                
                // Start audio processing coroutine
                launch {
                    processAudio()
                }
                
                Log.i(TAG, "Started wake word detection")
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            callback.onError("Failed to start listening: ${e.message}")
            false
        }
    }

    /**
     * Stop wake word detection
     */
    fun stopListening() {
        isListening = false
        
        try {
            audioRecord?.stop()
            Log.i(TAG, "Stopped wake word detection")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recording", e)
        }
    }

    /**
     * Release resources
     */
    fun release() {
        stopListening()
        job.cancel()
        
        try {
            audioRecord?.release()
            audioRecord = null
            
            tfliteInterpreter?.close()
            tfliteInterpreter = null
            
            Log.i(TAG, "Released detector resources")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        }
    }

    /**
     * Load TensorFlow Lite model
     */
    private fun loadTFLiteModel() {
        try {
            // Try to load from assets first, then from file system
            val modelBuffer = if (modelPath.startsWith("assets://")) {
                val assetPath = modelPath.removePrefix("assets://")
                context.assets.openFd(assetPath).use { fileDescriptor ->
                    FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                        val fileChannel = inputStream.channel
                        val startOffset = fileDescriptor.startOffset
                        val declaredLength = fileDescriptor.declaredLength
                        fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
                    }
                }
            } else {
                // Load from file system
                FileInputStream(modelPath).use { inputStream ->
                    val fileChannel = inputStream.channel
                    fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
                }
            }

            tfliteInterpreter = Interpreter(modelBuffer)
            Log.i(TAG, "TensorFlow Lite model loaded successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TensorFlow Lite model", e)
            throw e
        }
    }

    /**
     * Initialize audio recording with proper audio source for silent operation
     */
    private fun initializeAudioRecord() {
        // Use VOICE_RECOGNITION source to minimize system sounds and beeps
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            AUDIO_BUFFER_SIZE
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord not initialized properly")
        }
        
        Log.i(TAG, "AudioRecord initialized with buffer size: $AUDIO_BUFFER_SIZE (silent mode)")
    }

    /**
     * Main audio processing loop
     */
    private suspend fun processAudio() = withContext(Dispatchers.Default) {
        Log.d(TAG, "Started audio processing")
        
        while (isListening) {
            try {
                // Read audio data
                val bytesRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                
                if (bytesRead > 0) {
                    // Convert to float and accumulate in processing buffer
                    for (i in 0 until bytesRead) {
                        if (bufferIndex < processingBuffer.size) {
                            processingBuffer[bufferIndex] = audioBuffer[i].toFloat() / Short.MAX_VALUE
                            bufferIndex++
                        } else {
                            // Buffer full, process for wake word
                            processWakeWordDetection()
                            
                            // Shift buffer (keep last half for overlap)
                            val halfSize = processingBuffer.size / 2
                            System.arraycopy(processingBuffer, halfSize, processingBuffer, 0, halfSize)
                            bufferIndex = halfSize
                            
                            // Add current sample
                            processingBuffer[bufferIndex] = audioBuffer[i].toFloat() / Short.MAX_VALUE
                            bufferIndex++
                        }
                    }
                }
                
                // Small delay to prevent excessive CPU usage
                delay(10)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio processing", e)
                callback.onError("Audio processing error: ${e.message}")
                break
            }
        }
        
        Log.d(TAG, "Audio processing stopped")
    }

    /**
     * Process audio buffer for wake word detection
     */
    private fun processWakeWordDetection() {
        try {
            // Check cooldown period
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastDetectionTime < COOLDOWN_MS) {
                return
            }

            // Extract features from audio
            val features = featureExtractor.extractFeatures(processingBuffer)
            
            // Run inference
            val confidence = runInference(features)
            
            Log.v(TAG, "Wake word confidence: $confidence")
            
            // Check if detection threshold is met
            if (confidence >= DETECTION_THRESHOLD) {
                lastDetectionTime = currentTime
                Log.i(TAG, "Wake word detected with confidence: $confidence")
                
                // Notify callback on main thread
                launch(Dispatchers.Main) {
                    callback.onWakeWordDetected(confidence)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in wake word detection", e)
            callback.onError("Detection error: ${e.message}")
        }
    }

    /**
     * Run TensorFlow Lite inference
     */
    private fun runInference(features: FloatArray): Float {
        val interpreter = tfliteInterpreter ?: throw IllegalStateException("TFLite interpreter not initialized")
        
        try {
            // Prepare input tensor
            val inputBuffer = ByteBuffer.allocateDirect(features.size * 4)
                .order(ByteOrder.nativeOrder())
            
            for (feature in features) {
                inputBuffer.putFloat(feature)
            }
            inputBuffer.rewind()
            
            // Prepare output tensor
            val outputBuffer = ByteBuffer.allocateDirect(4) // Single float output
                .order(ByteOrder.nativeOrder())
            
            // Run inference
            interpreter.run(inputBuffer, outputBuffer)
            
            // Get result
            outputBuffer.rewind()
            return outputBuffer.float
            
        } catch (e: Exception) {
            Log.e(TAG, "Error running inference", e)
            throw e
        }
    }

    /**
     * Get model information
     */
    fun getModelInfo(): String {
        return tfliteInterpreter?.let { interpreter ->
            try {
                val inputTensor = interpreter.getInputTensor(0)
                val outputTensor = interpreter.getOutputTensor(0)
                
                "Input shape: ${inputTensor.shape().contentToString()}, " +
                "Output shape: ${outputTensor.shape().contentToString()}, " +
                "Model size: ${getModelSize()} bytes"
            } catch (e: Exception) {
                "Model info unavailable: ${e.message}"
            }
        } ?: "Model not loaded"
    }

    /**
     * Get approximate model size
     */
    private fun getModelSize(): Long {
        return try {
            if (modelPath.startsWith("assets://")) {
                val assetPath = modelPath.removePrefix("assets://")
                context.assets.openFd(assetPath).use { it.length }
            } else {
                java.io.File(modelPath).length()
            }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Test the detector with a sample
     */
    fun testDetector(): Float {
        // Generate a test audio sample (silence)
        val testSample = FloatArray(PROCESSING_BUFFER_SIZE) { 0f }
        val features = featureExtractor.extractFeatures(testSample)
        return runInference(features)
    }
}
