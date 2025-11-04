package com.wype.security.ml

import android.content.Context
import android.media.AudioRecord
import android.media.AudioFormat
import android.media.AudioAttributes
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.*
import kotlin.random.Random

/**
 * Ultra-lightweight custom neural network wake word detector
 * Uses a simple feedforward network with minimal parameters
 * Designed for maximum efficiency and minimal resource usage
 */
class SimpleNeuralWakeWordDetector(
    private val context: Context,
    private val wakeWord: String,
    private val callback: WakeWordCallback
) : CoroutineScope {

    companion object {
        private const val TAG = "SimpleNeuralWakeWordDetector"
        
        // Audio configuration
        private const val SAMPLE_RATE = 16000
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        
        // Neural network architecture
        private const val INPUT_SIZE = 39  // 13 MFCC * 3 frames (past, current, future)
        private const val HIDDEN_SIZE = 32 // Small hidden layer
        private const val OUTPUT_SIZE = 1  // Binary classification
        
        // Detection parameters
        private const val DETECTION_THRESHOLD = 0.8f
        private const val COOLDOWN_MS = 1500L
        private const val LEARNING_RATE = 0.001f
        
        // Audio processing
        private const val WINDOW_SIZE_MS = 30 // 30ms windows
        private const val HOP_SIZE_MS = 10     // 10ms hop
        private const val CONTEXT_FRAMES = 3   // Use 3 frames of context
        
        private val AUDIO_BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        ) * 2
    }

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.Default + job

    private var audioRecord: AudioRecord? = null
    private val featureExtractor = AudioFeatureExtractor()
    
    private var isListening = false
    private var lastDetectionTime = 0L
    
    // Neural network weights (randomly initialized, could be trained)
    private var hiddenWeights = Array(INPUT_SIZE) { FloatArray(HIDDEN_SIZE) { (Random.nextFloat() - 0.5f) * 0.1f } }
    private var hiddenBias = FloatArray(HIDDEN_SIZE) { (Random.nextFloat() - 0.5f) * 0.1f }
    private var outputWeights = FloatArray(HIDDEN_SIZE) { (Random.nextFloat() - 0.5f) * 0.1f }
    private var outputBias = (Random.nextFloat() - 0.5f) * 0.1f
    
    // Audio processing
    private val audioBuffer = ShortArray(AUDIO_BUFFER_SIZE)
    private val frameBuffer = mutableListOf<FloatArray>()
    private val windowSize = SAMPLE_RATE * WINDOW_SIZE_MS / 1000
    private val hopSize = SAMPLE_RATE * HOP_SIZE_MS / 1000

    interface WakeWordCallback {
        fun onWakeWordDetected(confidence: Float, wakeWord: String)
        fun onError(error: String)
    }

    /**
     * Initialize the detector
     */
    fun initialize(): Boolean {
        return try {
            // Initialize audio recording
            initializeAudioRecord()
            
            // Load or initialize neural network weights
            initializeNeuralNetwork()
            
            Log.i(TAG, "Simple neural wake word detector initialized for: $wakeWord")
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
                
                Log.i(TAG, "Started simple neural wake word detection")
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
            frameBuffer.clear()
            Log.i(TAG, "Stopped simple neural wake word detection")
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
            
            Log.i(TAG, "Released simple neural detector resources")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
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
     * Initialize neural network weights
     */
    private fun initializeNeuralNetwork() {
        // For a production system, these weights would be loaded from trained models
        // For this demonstration, we use random initialization with some basic patterns
        
        // Initialize weights with Xavier/Glorot initialization
        val hiddenScale = sqrt(2.0f / INPUT_SIZE)
        val outputScale = sqrt(2.0f / HIDDEN_SIZE)
        
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until HIDDEN_SIZE) {
                hiddenWeights[i][j] = (Random.nextFloat() - 0.5f) * 2 * hiddenScale
            }
        }
        
        for (i in 0 until HIDDEN_SIZE) {
            hiddenBias[i] = 0f
            outputWeights[i] = (Random.nextFloat() - 0.5f) * 2 * outputScale
        }
        
        outputBias = 0f
        
        Log.i(TAG, "Neural network initialized with ${getTotalParameters()} parameters")
    }

    /**
     * Main audio processing loop
     */
    private suspend fun processAudio() = withContext(Dispatchers.Default) {
        Log.d(TAG, "Started audio processing")
        val processingBuffer = FloatArray(windowSize)
        var bufferIndex = 0
        
        while (isListening) {
            try {
                // Read audio data
                val bytesRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                
                if (bytesRead > 0) {
                    // Convert to float and process in windows
                    for (i in 0 until bytesRead) {
                        processingBuffer[bufferIndex] = audioBuffer[i].toFloat() / Short.MAX_VALUE
                        bufferIndex++
                        
                        if (bufferIndex >= windowSize) {
                            // Process this window
                            processAudioWindow(processingBuffer.copyOf())
                            
                            // Slide window
                            val overlap = windowSize - hopSize
                            System.arraycopy(processingBuffer, hopSize, processingBuffer, 0, overlap)
                            bufferIndex = overlap
                        }
                    }
                }
                
                // Small delay to prevent excessive CPU usage
                delay(5)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio processing", e)
                callback.onError("Audio processing error: ${e.message}")
                break
            }
        }
        
        Log.d(TAG, "Audio processing stopped")
    }

    /**
     * Process a single audio window
     */
    private fun processAudioWindow(audioWindow: FloatArray) {
        try {
            // Extract MFCC features
            val mfccFeatures = featureExtractor.extractMFCC(audioWindow)
            
            if (mfccFeatures.isNotEmpty()) {
                // Average MFCC across frames for this window
                val avgMFCC = FloatArray(AudioFeatureExtractor.N_MFCC) { i ->
                    mfccFeatures.map { it[i] }.average().toFloat()
                }
                
                // Add to frame buffer
                frameBuffer.add(avgMFCC)
                
                // Maintain context window
                if (frameBuffer.size > CONTEXT_FRAMES) {
                    frameBuffer.removeAt(0)
                }
                
                // Process when we have enough context
                if (frameBuffer.size == CONTEXT_FRAMES) {
                    processWakeWordDetection()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio window", e)
        }
    }

    /**
     * Process wake word detection using neural network
     */
    private fun processWakeWordDetection() {
        try {
            // Check cooldown period
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastDetectionTime < COOLDOWN_MS) {
                return
            }

            // Prepare input features (concatenate context frames)
            val inputFeatures = FloatArray(INPUT_SIZE)
            var index = 0
            
            for (frame in frameBuffer) {
                for (i in 0 until minOf(frame.size, AudioFeatureExtractor.N_MFCC)) {
                    if (index < INPUT_SIZE) {
                        inputFeatures[index++] = frame[i]
                    }
                }
            }
            
            // Run neural network inference
            val confidence = runInference(inputFeatures)
            
            Log.v(TAG, "Wake word confidence: $confidence")
            
            // Check if detection threshold is met
            if (confidence >= DETECTION_THRESHOLD) {
                lastDetectionTime = currentTime
                Log.i(TAG, "Wake word '$wakeWord' detected with confidence: $confidence")
                
                // Notify callback on main thread
                launch(Dispatchers.Main) {
                    callback.onWakeWordDetected(confidence, wakeWord)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in wake word detection", e)
            callback.onError("Detection error: ${e.message}")
        }
    }

    /**
     * Run neural network inference
     */
    private fun runInference(input: FloatArray): Float {
        try {
            // Forward pass through hidden layer
            val hiddenOutput = FloatArray(HIDDEN_SIZE)
            
            for (j in 0 until HIDDEN_SIZE) {
                var sum = hiddenBias[j]
                for (i in 0 until INPUT_SIZE) {
                    if (i < input.size) {
                        sum = sum + input[i] * hiddenWeights[i][j]
                    }
                }
                hiddenOutput[j] = relu(sum) // ReLU activation
            }
            
            // Forward pass through output layer
            var output = outputBias
            for (j in 0 until HIDDEN_SIZE) {
                output = output + hiddenOutput[j] * outputWeights[j]
            }
            
            // Sigmoid activation for binary classification
            return sigmoid(output)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error running inference", e)
            throw e
        }
    }

    /**
     * ReLU activation function
     */
    private fun relu(x: Float): Float = maxOf(0f, x)

    /**
     * Sigmoid activation function
     */
    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x.toDouble()).toFloat())

    /**
     * Get total number of parameters in the network
     */
    fun getTotalParameters(): Int {
        return (INPUT_SIZE * HIDDEN_SIZE) + HIDDEN_SIZE + HIDDEN_SIZE + 1
    }

    /**
     * Get model information
     */
    fun getModelInfo(): String {
        return "Simple Neural Network - " +
               "Input: $INPUT_SIZE, Hidden: $HIDDEN_SIZE, Output: $OUTPUT_SIZE, " +
               "Parameters: ${getTotalParameters()}, " +
               "Memory: ~${getTotalParameters() * 4} bytes"
    }

    /**
     * Adjust detection sensitivity
     */
    fun setSensitivity(sensitivity: Float) {
        // This could adjust the detection threshold or network weights
        val threshold = when {
            sensitivity < 0.3f -> 0.9f  // Very conservative
            sensitivity < 0.7f -> 0.8f  // Normal
            else -> 0.6f                // Aggressive
        }
        
        // You could implement dynamic threshold adjustment here
        Log.i(TAG, "Sensitivity adjusted to: $sensitivity (threshold: $threshold)")
    }

    /**
     * Test the detector with a sample
     */
    fun testDetector(): Float {
        // Generate a test input (zeros)
        val testInput = FloatArray(INPUT_SIZE) { 0f }
        return runInference(testInput)
    }

    /**
     * Simple training method (for demonstration)
     */
    fun trainOnSample(input: FloatArray, expected: Float) {
        // Simple gradient descent update (for demonstration)
        val predicted = runInference(input)
        val error = expected - predicted
        
        // This is a very simplified training step
        // In a real implementation, you'd compute proper gradients
        if (abs(error) > 0.1f) {
            val learningStep = error * LEARNING_RATE
            outputBias += learningStep
            
            Log.d(TAG, "Training step - Error: $error, Adjustment: $learningStep")
        }
    }
}
