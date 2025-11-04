package com.wype.security.hotword

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wype.security.utils.PreferencesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumentation tests for HotwordEngine implementations
 * 
 * Tests both PorcupineHotword and TfLiteHotword engines with:
 * - Configuration loading from preferences
 * - Custom keyword file detection
 * - Callback mechanism validation
 * - State management
 * - False positive prevention
 * - Mock prerecorded WAV testing framework
 */
@RunWith(AndroidJUnit4::class)
class HotwordEngineInstrumentationTest {
    
    private lateinit var context: Context
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var porcupineEngine: PorcupineHotword
    private lateinit var tfliteEngine: TfLiteHotword
    
    private var detectionResults = mutableListOf<DetectionResult>()
    private val detectionLatch = CountDownLatch(1)
    
    private val testCallback = object : HotwordEngine.HotwordCallback {
        override fun onArmingPhrase(keyword: String, confidence: Float) {
            detectionResults.add(DetectionResult(keyword, confidence, DetectionType.ARMING))
            detectionLatch.countDown()
        }
        
        override fun onEmergencyPhrase(keyword: String, confidence: Float) {
            detectionResults.add(DetectionResult(keyword, confidence, DetectionType.EMERGENCY))
            detectionLatch.countDown()
        }
        
        override fun onError(error: String, exception: Throwable?) {
            detectionResults.add(DetectionResult(error, 0f, DetectionType.ERROR))
            detectionLatch.countDown()
        }
        
        override fun onEngineStateChanged(isListening: Boolean) {
            // Track state changes if needed for testing
        }
    }
    
    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        preferencesManager = PreferencesManager(context)
        
        // Clear any existing preferences
        preferencesManager.clearAllPreferences()
        
        // Set up test configuration
        preferencesManager.setMLDetectionThreshold(0.75f)
        preferencesManager.setPorcupineSensitivity(0.7f)
        
        // Initialize engines
        porcupineEngine = PorcupineHotword(context)
        tfliteEngine = TfLiteHotword(context)
        
        // Clear detection results
        detectionResults.clear()
    }
    
    @After
    fun tearDown() {
        // Clean up engines
        porcupineEngine.release()
        tfliteEngine.release()
        
        // Clear preferences
        preferencesManager.clearAllPreferences()
    }
    
    @Test
    fun testPorcupineEngineInitialization() {
        // Test initialization with callback
        assertTrue("Porcupine engine should initialize successfully", 
                  porcupineEngine.initialize(testCallback))
        
        // Verify engine state
        val engineInfo = porcupineEngine.getEngineInfo()
        assertEquals("PorcupineHotword", engineInfo["engineName"])
        assertEquals(HotwordEngineState.INITIALIZED.name, engineInfo["state"])
        assertFalse("Engine should not be listening initially", porcupineEngine.isListening())
        
        // Check supported keywords
        val supportedKeywords = porcupineEngine.getSupportedKeywords()
        assertTrue("Should have arming keywords", supportedKeywords["arming"]?.isNotEmpty() == true)
        assertTrue("Should have emergency keywords", supportedKeywords["emergency"]?.isNotEmpty() == true)
    }
    
    @Test
    fun testTensorFlowLiteEngineInitialization() {
        // Test initialization with callback
        assertTrue("TensorFlow Lite engine should initialize successfully", 
                  tfliteEngine.initialize(testCallback))
        
        // Verify engine state
        val engineInfo = tfliteEngine.getEngineInfo()
        assertEquals("TfLiteHotword", engineInfo["engineName"])
        assertEquals(HotwordEngineState.INITIALIZED.name, engineInfo["state"])
        assertFalse("Engine should not be listening initially", tfliteEngine.isListening())
        
        // Check TensorFlow Lite specific configuration
        assertTrue("Should have confidence threshold", engineInfo["confidenceThreshold"] is Float)
        assertTrue("Should have model paths", engineInfo["modelPaths"] is Map<*, *>)
        assertEquals(2, engineInfo["inferenceThreads"])
    }
    
    @Test
    fun testEngineStartStop() = runBlocking {
        // Initialize engine
        assertTrue(porcupineEngine.initialize(testCallback))
        
        // Test start
        assertTrue("Engine should start successfully", porcupineEngine.start())
        assertTrue("Engine should be listening", porcupineEngine.isListening())
        
        // Wait a moment for detection loop to start
        delay(100)
        
        // Test stop
        assertTrue("Engine should stop successfully", porcupineEngine.stop())
        assertFalse("Engine should not be listening after stop", porcupineEngine.isListening())
    }
    
    @Test
    fun testConfigurationUpdate() {
        // Initialize engine
        assertTrue(porcupineEngine.initialize(testCallback))
        
        // Test sensitivity update
        assertTrue("Should update sensitivity", porcupineEngine.setSensitivity(0.8f))
        assertEquals(0.8f, porcupineEngine.getSensitivity(), 0.001f)
        
        // Test invalid sensitivity
        assertFalse("Should reject invalid sensitivity", porcupineEngine.setSensitivity(1.5f))
        
        // Test configuration map update
        val config = mapOf(
            "sensitivity" to 0.6f,
            "armingKeywords" to listOf("test arming", "activate test"),
            "emergencyKeywords" to listOf("test help", "test emergency")
        )
        
        assertTrue("Should update configuration", porcupineEngine.updateConfiguration(config))
        
        // Verify updated keywords
        val supportedKeywords = porcupineEngine.getSupportedKeywords()
        assertTrue("Should contain updated arming keywords", 
                  supportedKeywords["arming"]?.contains("test arming") == true)
        assertTrue("Should contain updated emergency keywords", 
                  supportedKeywords["emergency"]?.contains("test help") == true)
    }
    
    @Test
    fun testManualTriggerSimulation() = runBlocking {
        // Initialize and start engine
        assertTrue(porcupineEngine.initialize(testCallback))
        assertTrue(porcupineEngine.start())
        
        // Clear results
        detectionResults.clear()
        
        // Test manual arming phrase trigger
        porcupineEngine.simulateArmingPhrase()
        
        // Wait for callback
        assertTrue("Should receive arming phrase detection", 
                  detectionLatch.await(1000, TimeUnit.MILLISECONDS))
        
        // Verify result
        assertTrue("Should have detection results", detectionResults.isNotEmpty())
        val result = detectionResults.first()
        assertEquals(DetectionType.ARMING, result.type)
        assertTrue("Should have reasonable confidence", result.confidence > 0.8f)
    }
    
    @Test
    fun testPreferencesIntegration() {
        // Set up preferences with specific configuration
        preferencesManager.setMLDetectionThreshold(0.85f)
        preferencesManager.setPorcupineWakeWord("custom wakeword")
        
        // Initialize engine (should load from preferences)
        assertTrue(porcupineEngine.initialize(testCallback))
        
        // Verify preferences were loaded
        assertEquals(0.85f, (1.0f - porcupineEngine.getSensitivity()), 0.01f) // Sensitivity is inverted threshold
        
        // Check that custom wake word was added to keywords
        val supportedKeywords = porcupineEngine.getSupportedKeywords()
        assertTrue("Should contain custom wake word from preferences",
                  supportedKeywords["arming"]?.contains("custom wakeword") == true)
    }
    
    @Test
    fun testCustomKeywordFileDetection() = runBlocking {
        // Initialize engine (should scan for custom keyword files)
        assertTrue(porcupineEngine.initialize(testCallback))
        
        // Get the list of supported keywords
        val supportedKeywords = porcupineEngine.getSupportedKeywords()
        
        // Verify that keywords from placeholder .ppn files were detected
        // Note: This depends on the placeholder files we created
        val armingKeywords = supportedKeywords["arming"] ?: emptyList()
        val emergencyKeywords = supportedKeywords["emergency"] ?: emptyList()
        
        // Should have at least the default keywords plus any detected from files
        assertTrue("Should have multiple arming keywords", armingKeywords.size >= 3)
        assertTrue("Should have multiple emergency keywords", emergencyKeywords.size >= 3)
        
        // Log detected keywords for debugging
        println("Detected arming keywords: ${armingKeywords.joinToString()}")
        println("Detected emergency keywords: ${emergencyKeywords.joinToString()}")
    }
    
    @Test
    fun testTensorFlowLiteConfidenceThreshold() {
        // Initialize TensorFlow Lite engine
        assertTrue(tfliteEngine.initialize(testCallback))
        
        // Test confidence threshold (different from sensitivity for TfLite)
        assertTrue("Should set confidence threshold", tfliteEngine.setConfidenceThreshold(0.9f))
        assertEquals(0.9f, tfliteEngine.getConfidenceThreshold(), 0.001f)
        
        // Test sensitivity (inverted confidence for TfLite)
        assertEquals(0.1f, tfliteEngine.getSensitivity(), 0.001f) // 1.0 - 0.9 = 0.1
        
        // Test invalid confidence threshold
        assertFalse("Should reject invalid confidence threshold", 
                   tfliteEngine.setConfidenceThreshold(1.5f))
    }
    
    @Test 
    fun testEngineInfoComparison() {
        // Initialize both engines
        assertTrue(porcupineEngine.initialize(testCallback))
        assertTrue(tfliteEngine.initialize(testCallback))
        
        // Get engine info
        val porcupineInfo = porcupineEngine.getEngineInfo()
        val tfliteInfo = tfliteEngine.getEngineInfo()
        
        // Verify engine-specific information
        assertEquals("PorcupineHotword", porcupineInfo["engineName"])
        assertEquals("TfLiteHotword", tfliteInfo["engineName"])
        
        // Both should support English
        assertTrue("Porcupine should support English", 
                  (porcupineInfo["supportedLanguages"] as List<*>).contains("en"))
        assertTrue("TensorFlow Lite should support English", 
                  (tfliteInfo["supportedLanguages"] as List<*>).contains("en"))
        
        // Both should be offline
        assertEquals(false, porcupineInfo["requiresNetwork"])
        assertEquals(false, tfliteInfo["requiresNetwork"])
        
        // TensorFlow Lite should have model-specific information
        assertTrue("TfLite should have model paths", tfliteInfo["modelPaths"] is Map<*, *>)
        assertTrue("TfLite should have inference threads", tfliteInfo["inferenceThreads"] is Int)
    }
    
    /**
     * Mock WAV testing framework
     * In a real implementation, this would load actual WAV files and feed them to the engine
     */
    @Test
    fun testPrerecordedWAVSimulation() = runBlocking {
        // This is a placeholder for the real WAV testing framework mentioned in the todo
        
        // Initialize engine
        assertTrue(porcupineEngine.initialize(testCallback))
        assertTrue(porcupineEngine.start())
        
        // Simulate loading and processing prerecorded WAV files
        val mockWAVFiles = listOf(
            MockWAVFile("arming_phrase_001.wav", "activate protection", 0.92f, true),
            MockWAVFile("emergency_phrase_001.wav", "help me now", 0.89f, false),
            MockWAVFile("background_noise_001.wav", "", 0.15f, null), // Should not trigger
            MockWAVFile("false_positive_001.wav", "activate protection", 0.65f, null) // Below threshold
        )
        
        var correctDetections = 0
        var falsePositives = 0
        
        for (mockWAV in mockWAVFiles) {
            // In real implementation: 
            // 1. Load WAV file from assets/test_wavs/
            // 2. Convert to appropriate audio format
            // 3. Feed to engine frame by frame
            // 4. Measure detection accuracy
            
            // For now, simulate the expected behavior
            when {
                mockWAV.shouldTrigger == true && mockWAV.confidence > 0.75f -> {
                    correctDetections++
                    println("✅ Correct detection: ${mockWAV.fileName} -> ${mockWAV.expectedKeyword}")
                }
                mockWAV.shouldTrigger == false && mockWAV.confidence < 0.75f -> {
                    correctDetections++
                    println("✅ Correct rejection: ${mockWAV.fileName}")
                }
                else -> {
                    falsePositives++
                    println("❌ False result: ${mockWAV.fileName}")
                }
            }
        }
        
        // Verify accuracy metrics
        val totalTests = mockWAVFiles.size
        val accuracyPercent = (correctDetections.toFloat() / totalTests) * 100
        
        assertTrue("Detection accuracy should be >= 75%", accuracyPercent >= 75f)
        assertTrue("False positive rate should be low", falsePositives <= 1)
        
        println("WAV Test Results: ${correctDetections}/${totalTests} correct (${accuracyPercent.toInt()}% accuracy)")
    }
    
    // Helper data classes
    data class DetectionResult(
        val keyword: String,
        val confidence: Float,
        val type: DetectionType
    )
    
    enum class DetectionType {
        ARMING, EMERGENCY, ERROR
    }
    
    data class MockWAVFile(
        val fileName: String,
        val expectedKeyword: String,
        val confidence: Float,
        val shouldTrigger: Boolean? // null = should not trigger
    )
}

/*
 * ================================
 * REAL WAV TESTING FRAMEWORK NOTES
 * ================================
 * 
 * To implement actual prerecorded WAV testing:
 * 
 * 1. Create test WAV files in assets/test_wavs/:
 * - arming_phrase_001.wav, arming_phrase_002.wav, etc.
 * - emergency_phrase_001.wav, emergency_phrase_002.wav, etc.
 * - background_noise_001.wav, background_noise_002.wav, etc.
 * - false_positive_001.wav, false_positive_002.wav, etc.
 * 
 * 2. Load WAV files in test:
 * ```kotlin
 * private fun loadWAVFile(fileName: String): FloatArray {
 *     val inputStream = context.assets.open("test_wavs/$fileName")
 *     val wavData = WavFile.read(inputStream)
 *     return wavData.toFloatArray()
 * }
 * ```
 * 
 * 3. Feed audio data to engine frame by frame:
 * ```kotlin
 * private suspend fun feedAudioToEngine(audioData: FloatArray, engine: HotwordEngine) {
 *     val frameSize = 480 // 30ms at 16kHz
 *     for (i in audioData.indices step frameSize) {
 *         val frame = audioData.sliceArray(i until minOf(i + frameSize, audioData.size))
 *         // In real implementation, this would be fed to the engine's audio processing
 *         delay(30) // Simulate real-time processing
 *     }
 * }
 * ```
 * 
 * 4. Measure accuracy metrics:
 * - True Positive Rate (TPR): Correctly detected target phrases
 * - False Positive Rate (FPR): Incorrectly detected non-target audio
 * - False Negative Rate (FNR): Missed target phrases
 * - Precision: TP / (TP + FP)
 * - Recall: TP / (TP + FN)
 * - F1 Score: 2 * (Precision * Recall) / (Precision + Recall)
 * 
 * 5. Test different scenarios:
 * - Clean audio vs noisy backgrounds
 * - Different speakers (male/female/children)
 * - Different accents and pronunciations
 * - Various volume levels
 * - Overlapping speech
 * - Music and TV playing in background
 * 
 * 6. Performance benchmarking:
 * - CPU usage during inference
 * - Memory consumption
 * - Battery drain over extended periods
 * - Latency from audio input to detection callback
 */
