package com.wype.security.hotword

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Complete KWS Integration Demonstration
 * Shows the full workflow from arming to emergency alert
 */
class KWSIntegrationDemo {
    
    companion object {
        private const val TAG = "KWSIntegrationDemo"
        
        /**
         * Demonstrate complete KWS workflow
         * 1. Start service with engine
         * 2. Detect arming phrase -> arm protection
         * 3. Detect emergency phrase -> fire alert worker
         */
        fun demonstrateCompleteWorkflow(context: Context, scope: CoroutineScope) {
            Log.d(TAG, "=== KWS Integration Complete Workflow Demo ===")
            
            scope.launch {
                try {
                    // Step 1: Start the hotword service
                    Log.i(TAG, "1. Starting WypeHotwordService...")
                    startHotwordService(context)
                    
                    delay(2000) // Allow service to initialize
                    
                    // Step 2: Check initial state
                    logSystemState(context)
                    
                    // Step 3: Simulate arming phrase detection
                    Log.i(TAG, "3. Simulating arming phrase detection...")
                    simulateArmingPhrase(context)
                    
                    delay(1000)
                    
                    // Step 4: Verify protection mode is armed
                    val isArmed = ProtectionMode.isArmed(context)
                    Log.i(TAG, "4. Protection mode after arming: ${if (isArmed) "ARMED ✅" else "NOT ARMED ❌"}")
                    
                    // Step 5: Simulate emergency phrase detection
                    Log.i(TAG, "5. Simulating emergency phrase detection...")
                    simulateEmergencyPhrase(context)
                    
                    delay(1000)
                    
                    // Step 6: Check alert system status
                    Log.i(TAG, "6. Alert system status: ${AlertGate.getStatus()}")
                    
                    Log.i(TAG, "✅ Complete KWS workflow demonstration finished")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in workflow demo", e)
                }
            }
        }
        
        /**
         * Demonstrate swappable engine architecture
         */
        fun demonstrateSwappableEngines(context: Context) {
            Log.d(TAG, "=== Swappable Engine Architecture Demo ===")
            
            try {
                // Demo 1: Porcupine engine
                val porcupineEngine = PorcupineHotword(context)
                val engineInfo = porcupineEngine.getEngineInfo()
                Log.i(TAG, "Porcupine engine info: $engineInfo")
                
                // Demo 2: Show how to swap engines
                Log.i(TAG, "Engine swapping demonstration:")
                Log.i(TAG, "• Current engine: PorcupineHotword")
                Log.i(TAG, "• Could swap to: TensorFlowLiteHotword, CustomMLHotword, etc.")
                Log.i(TAG, "• All engines implement the same HotwordEngine interface")
                
                val supportedKeywords = porcupineEngine.getSupportedKeywords()
                Log.i(TAG, "Supported keywords: $supportedKeywords")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error demonstrating engines", e)
            }
        }
        
        /**
         * Test the protection mode workflow
         */
        fun testProtectionModeWorkflow(context: Context, scope: CoroutineScope) {
            Log.d(TAG, "=== Protection Mode Workflow Test ===")
            
            scope.launch {
                try {
                    // Initial state - protection should be off
                    ProtectionMode.setArmed(context, false)
                    Log.i(TAG, "Initial state - Protection: ${if (ProtectionMode.isArmed(context)) "ARMED" else "DISARMED"}")
                    
                    // Test 1: Emergency phrase when NOT armed (should be ignored)
                    Log.i(TAG, "Test 1: Emergency phrase when protection is OFF")
                    simulateEmergencyPhrase(context)
                    delay(500)
                    
                    // Test 2: Arming phrase (should arm protection)
                    Log.i(TAG, "Test 2: Arming phrase detection")
                    simulateArmingPhrase(context)
                    delay(500)
                    
                    val isArmedAfterArming = ProtectionMode.isArmed(context)
                    Log.i(TAG, "Protection after arming: ${if (isArmedAfterArming) "ARMED ✅" else "NOT ARMED ❌"}")
                    
                    // Test 3: Emergency phrase when armed (should trigger alert)
                    Log.i(TAG, "Test 3: Emergency phrase when protection is ON")
                    simulateEmergencyPhrase(context)
                    delay(1000)
                    
                    Log.i(TAG, "✅ Protection mode workflow test completed")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in protection mode test", e)
                }
            }
        }
        
        /**
         * Test alert spam prevention
         */
        fun testSpamPrevention(context: Context, scope: CoroutineScope) {
            Log.d(TAG, "=== Alert Spam Prevention Test ===")
            
            scope.launch {
                try {
                    // Ensure protection is armed
                    ProtectionMode.setArmed(context, true)
                    
                    // Reset alert gate for test
                    AlertGate.forceReset()
                    
                    // Test rapid emergency phrase detections
                    Log.i(TAG, "Sending multiple emergency phrases rapidly...")
                    
                    for (i in 1..5) {
                        Log.i(TAG, "Emergency attempt $i:")
                        simulateEmergencyPhrase(context)
                        
                        val gateStatus = AlertGate.getStatus()
                        Log.i(TAG, "  Alert gate: $gateStatus")
                        
                        delay(200) // Small delay between attempts
                    }
                    
                    Log.i(TAG, "✅ Spam prevention test completed - only first alert should have been sent")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in spam prevention test", e)
                }
            }
        }
        
        /**
         * Helper: Start the hotword service
         */
        private fun startHotwordService(context: Context) {
            val intent = Intent(context, WypeHotwordService::class.java).apply {
                action = WypeHotwordService.ACTION_START_HOTWORD_DETECTION
            }
            context.startForegroundService(intent)
            Log.i(TAG, "Hotword service start requested")
        }
        
        /**
         * Helper: Simulate arming phrase detection
         */
        private fun simulateArmingPhrase(context: Context) {
            // In a real app, you might need to get the service instance differently
            // This is just for demonstration
            Log.d(TAG, "🧪 Simulating arming phrase: 'activate protection'")
            
            // Direct simulation - in real app, this would come from actual KWS detection
            val wasArmed = ProtectionMode.isArmed(context)
            if (!wasArmed) {
                ProtectionMode.setArmed(context, true)
                Log.w(TAG, "🛡️ PROTECTION MODE ARMED by arming phrase simulation")
            }
        }
        
        /**
         * Helper: Simulate emergency phrase detection
         */
        private fun simulateEmergencyPhrase(context: Context) {
            Log.d(TAG, "🧪 Simulating emergency phrase: 'help me now'")
            
            val isArmed = ProtectionMode.isArmed(context)
            if (isArmed) {
                // This would normally be handled by the service's emergency phrase callback
                Log.w(TAG, "🚨 Emergency phrase detected - protection is ARMED")
                
                // Check alert gate
                if (AlertGate.isOpen()) {
                    Log.w(TAG, "🚨 Alert gate is open - would trigger emergency worker")
                } else {
                    Log.w(TAG, "❌ Alert gate is closed - emergency blocked by cooldown")
                }
            } else {
                Log.i(TAG, "Emergency phrase ignored - protection not armed")
            }
        }
        
        /**
         * Helper: Log current system state
         */
        private fun logSystemState(context: Context) {
            Log.i(TAG, "--- System State ---")
            Log.i(TAG, "Protection Mode: ${if (ProtectionMode.isArmed(context)) "ARMED" else "DISARMED"}")
            Log.i(TAG, "Alert Gate: ${AlertGate.getStatus()}")
            Log.i(TAG, "Service Running: ${WypeHotwordService.isRunning()}")
        }
        
        /**
         * Demonstrate engine configuration
         */
        fun demonstrateEngineConfiguration(context: Context) {
            Log.d(TAG, "=== Engine Configuration Demo ===")
            
            try {
                val engine = PorcupineHotword(context)
                
                // Initialize with callback
                val callback = object : HotwordEngine.HotwordCallback {
                    override fun onArmingPhrase(keyword: String, confidence: Float) {
                        Log.i(TAG, "Config demo - Arming: $keyword")
                    }
                    override fun onEmergencyPhrase(keyword: String, confidence: Float) {
                        Log.i(TAG, "Config demo - Emergency: $keyword")
                    }
                    override fun onError(error: String, exception: Throwable?) {
                        Log.e(TAG, "Config demo - Error: $error")
                    }
                    override fun onEngineStateChanged(isListening: Boolean) {
                        Log.d(TAG, "Config demo - State: $isListening")
                    }
                }
                
                if (engine.initialize(callback)) {
                    // Show initial configuration
                    Log.i(TAG, "Initial sensitivity: ${engine.getSensitivity()}")
                    Log.i(TAG, "Supported keywords: ${engine.getSupportedKeywords()}")
                    
                    // Update configuration
                    engine.setSensitivity(0.9f)
                    Log.i(TAG, "Updated sensitivity: ${engine.getSensitivity()}")
                    
                    val config = mapOf(
                        "armingKeywords" to listOf("custom arming phrase", "enable guard"),
                        "emergencyKeywords" to listOf("custom emergency", "urgent help")
                    )
                    
                    if (engine.updateConfiguration(config)) {
                        Log.i(TAG, "Configuration updated successfully")
                        Log.i(TAG, "New keywords: ${engine.getSupportedKeywords()}")
                    }
                    
                    engine.release()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in configuration demo", e)
            }
        }
    }
}

/*
 * ================================
 * INTEGRATION EXAMPLES
 * ================================
 * 
 * Example usage in MainActivity:
 * ```kotlin
 * class MainActivity : AppCompatActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         
 *         // Demonstrate complete KWS workflow
 *         KWSIntegrationDemo.demonstrateCompleteWorkflow(this, lifecycleScope)
 *         
 *         // Test protection mode workflow
 *         KWSIntegrationDemo.testProtectionModeWorkflow(this, lifecycleScope)
 *         
 *         // Test spam prevention
 *         KWSIntegrationDemo.testSpamPrevention(this, lifecycleScope)
 *     }
 * }
 * ```
 * 
 * Expected workflow:
 * 1. Service starts with KWS engine silently in background
 * 2. User says arming phrase -> ProtectionMode.setArmed(true) 
 * 3. User says emergency phrase -> onHotwordDetected() -> SendAlertWorker fires once
 * 4. Subsequent emergency phrases within 3-minute cooldown are blocked
 * 5. System remains armed until manually disarmed
 * 
 * Testing commands:
 * ```kotlin
 * // In development/testing, you can manually trigger:
 * val service = WypeHotwordService()
 * service.simulateArmingPhrase()    // Arms protection
 * service.simulateEmergencyPhrase() // Triggers alert (if armed)
 * ```
 * 
 * Engine swapping example:
 * ```kotlin
 * class CustomHotwordEngine : HotwordEngine {
 *     // Implement all interface methods
 *     // Could be TensorFlow Lite, custom ML, cloud-based, etc.
 * }
 * 
 * // In WypeHotwordService.initializeHotwordEngine():
 * hotwordEngine = when (preferredEngine) {
 *     "porcupine" -> PorcupineHotword(this)
 *     "tensorflow" -> TensorFlowHotword(this) 
 *     "custom" -> CustomHotwordEngine(this)
 *     else -> PorcupineHotword(this) // Default
 * }
 * ```
 */
