package com.wype.security.hotword

/**
 * Swappable Keyword Spotting (KWS) Engine Interface
 * 
 * Provides a common interface for different hotword detection implementations
 * such as Porcupine, TensorFlow Lite, custom neural networks, etc.
 * 
 * Features:
 * - Pluggable architecture for different KWS engines
 * - Standardized lifecycle management (start/stop)
 * - Callback-based detection notifications
 * - Engine state management and configuration
 */
interface HotwordEngine {
    
    /**
     * Callback interface for hotword detection events
     */
    interface HotwordCallback {
        
        /**
         * Called when an arming phrase is detected
         * This should typically enable protection mode
         * 
         * @param keyword The detected arming keyword/phrase
         * @param confidence Detection confidence (0.0 to 1.0)
         */
        fun onArmingPhrase(keyword: String, confidence: Float)
        
        /**
         * Called when an emergency phrase is detected
         * This should trigger emergency alert procedures
         * 
         * @param keyword The detected emergency keyword/phrase
         * @param confidence Detection confidence (0.0 to 1.0)
         */
        fun onEmergencyPhrase(keyword: String, confidence: Float)
        
        /**
         * Called when the engine encounters an error
         * 
         * @param error Error message
         * @param exception Optional exception details
         */
        fun onError(error: String, exception: Throwable? = null)
        
        /**
         * Called when the engine state changes
         * 
         * @param isListening True if engine is actively listening
         */
        fun onEngineStateChanged(isListening: Boolean)
    }
    
    /**
     * Initialize the hotword engine
     * 
     * @param callback Callback for detection events
     * @return True if initialization successful, false otherwise
     */
    fun initialize(callback: HotwordCallback): Boolean
    
    /**
     * Start hotword detection
     * Engine will begin listening for configured keywords
     * 
     * @return True if started successfully, false otherwise
     */
    fun start(): Boolean
    
    /**
     * Stop hotword detection
     * Engine will stop listening and release audio resources
     * 
     * @return True if stopped successfully, false otherwise
     */
    fun stop(): Boolean
    
    /**
     * Release all engine resources
     * Should be called when engine is no longer needed
     */
    fun release()
    
    /**
     * Check if the engine is currently listening
     * 
     * @return True if actively listening for hotwords
     */
    fun isListening(): Boolean
    
    /**
     * Get engine information and status
     * 
     * @return Map containing engine details (name, version, status, etc.)
     */
    fun getEngineInfo(): Map<String, Any>
    
    /**
     * Update engine configuration
     * 
     * @param config Configuration parameters specific to the engine
     * @return True if configuration updated successfully
     */
    fun updateConfiguration(config: Map<String, Any>): Boolean
    
    /**
     * Get supported keywords for this engine
     * 
     * @return List of keyword categories and their supported words
     */
    fun getSupportedKeywords(): Map<String, List<String>>
    
    /**
     * Set sensitivity for detection
     * 
     * @param sensitivity Value between 0.0 (less sensitive) and 1.0 (more sensitive)
     * @return True if sensitivity set successfully
     */
    fun setSensitivity(sensitivity: Float): Boolean
    
    /**
     * Get current detection sensitivity
     * 
     * @return Current sensitivity value (0.0 to 1.0)
     */
    fun getSensitivity(): Float
}

/**
 * Engine configuration data class for standardized configuration
 */
data class HotwordEngineConfig(
    val engineName: String,
    val version: String,
    val armingKeywords: List<String> = listOf("arm protection", "activate guard", "start watching"),
    val emergencyKeywords: List<String> = listOf("help me", "emergency", "call help"),
    val sensitivity: Float = 0.7f,
    val enableContinuousListening: Boolean = true,
    val bufferSizeMs: Int = 1000,
    val customParameters: Map<String, Any> = emptyMap()
)

/**
 * Engine state enumeration
 */
enum class HotwordEngineState {
    UNINITIALIZED,    // Engine not yet initialized
    INITIALIZED,      // Engine initialized but not listening
    LISTENING,        // Engine actively listening for keywords
    STOPPED,          // Engine stopped listening
    ERROR,            // Engine in error state
    RELEASED          // Engine resources released
}

/**
 * Factory interface for creating hotword engines
 */
interface HotwordEngineFactory {
    
    /**
     * Create a new hotword engine instance
     * 
     * @param config Engine configuration
     * @return New engine instance
     */
    fun createEngine(config: HotwordEngineConfig): HotwordEngine
    
    /**
     * Get available engine types
     * 
     * @return List of supported engine types
     */
    fun getAvailableEngines(): List<String>
    
    /**
     * Check if a specific engine type is available
     * 
     * @param engineType Engine type identifier
     * @return True if engine is available
     */
    fun isEngineAvailable(engineType: String): Boolean
}

/*
 * ================================
 * USAGE EXAMPLES
 * ================================
 * 
 * Basic engine usage:
 * ```kotlin
 * class MyHotwordService : Service() {
 *     private var engine: HotwordEngine? = null
 *     
 *     private val callback = object : HotwordEngine.HotwordCallback {
 *         override fun onArmingPhrase(keyword: String, confidence: Float) {
 *             Log.i("Hotword", "Arming phrase detected: $keyword")
 *             ProtectionMode.setArmed(this@MyHotwordService, true)
 *         }
 *         
 *         override fun onEmergencyPhrase(keyword: String, confidence: Float) {
 *             Log.w("Hotword", "Emergency phrase detected: $keyword")
 *             if (ProtectionMode.isArmed(this@MyHotwordService)) {
 *                 triggerEmergencyAlert(keyword, confidence)
 *             }
 *         }
 *         
 *         override fun onError(error: String, exception: Throwable?) {
 *             Log.e("Hotword", "Engine error: $error", exception)
 *         }
 *         
 *         override fun onEngineStateChanged(isListening: Boolean) {
 *             Log.d("Hotword", "Engine listening: $isListening")
 *         }
 *     }
 *     
 *     fun startHotwordDetection() {
 *         engine = PorcupineHotwordEngine() // or any other implementation
 *         if (engine?.initialize(callback) == true) {
 *             engine?.start()
 *         }
 *     }
 *     
 *     fun stopHotwordDetection() {
 *         engine?.stop()
 *         engine?.release()
 *         engine = null
 *     }
 * }
 * ```
 * 
 * Engine factory usage:
 * ```kotlin
 * class HotwordManager {
 *     fun createOptimalEngine(): HotwordEngine {
 *         val factory = HotwordEngineFactory.getInstance()
 *         
 *         val config = HotwordEngineConfig(
 *             engineName = "porcupine",
 *             version = "3.0",
 *             armingKeywords = listOf("activate protection", "start guard"),
 *             emergencyKeywords = listOf("help me now", "emergency alert"),
 *             sensitivity = 0.8f
 *         )
 *         
 *         return when {
 *             factory.isEngineAvailable("porcupine") -> factory.createEngine(config.copy(engineName = "porcupine"))
 *             factory.isEngineAvailable("tensorflow") -> factory.createEngine(config.copy(engineName = "tensorflow"))
 *             else -> factory.createEngine(config.copy(engineName = "fallback"))
 *         }
 *     }
 * }
 * ```
 * 
 * Configuration updates:
 * ```kotlin
 * fun updateEngineSettings(engine: HotwordEngine) {
 *     // Update sensitivity
 *     engine.setSensitivity(0.9f)
 *     
 *     // Update configuration
 *     val newConfig = mapOf(
 *         "bufferSize" to 1500,
 *         "enableNoiseReduction" to true,
 *         "debugMode" to false
 *     )
 *     
 *     engine.updateConfiguration(newConfig)
 *     
 *     // Check current status
 *     val info = engine.getEngineInfo()
 *     Log.d("Engine", "Status: $info")
 * }
 * ```
 */
