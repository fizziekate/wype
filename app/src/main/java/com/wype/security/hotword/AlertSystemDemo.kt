package com.wype.security.hotword

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import kotlinx.coroutines.*

/**
 * Comprehensive demonstration of the complete alert system
 * Shows integration between AlertGate, SendAlertWorker, ProtectionMode, and WypeHotwordService
 */
class AlertSystemDemo {
    
    companion object {
        private const val TAG = "AlertSystemDemo"
        
        /**
         * Demonstrate complete alert flow from hotword detection to alert delivery
         */
        fun demonstrateCompleteAlertFlow(context: Context) {
            Log.d(TAG, "=== Complete Alert System Demo ===")
            
            try {
                // 1. Check initial system state
                logSystemState(context)
                
                // 2. Test alert gate functionality
                demonstrateAlertGate()
                
                // 3. Test protection mode integration
                demonstrateProtectionModeIntegration(context)
                
                // 4. Simulate hotword detection with different scenarios
                simulateHotwordScenarios(context)
                
                // 5. Show WorkManager queue status
                showWorkManagerStatus(context)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in alert system demo", e)
            }
        }
        
        /**
         * Log current system state for debugging
         */
        private fun logSystemState(context: Context) {
            Log.i(TAG, "--- System State ---")
            Log.i(TAG, "Protection Mode: ${if (ProtectionMode.isArmed(context)) "ARMED" else "DISARMED"}")
            Log.i(TAG, "Alert Gate: ${AlertGate.getStatus()}")
            Log.i(TAG, "Hotword Service: ${if (WypeHotwordService.isRunning()) "RUNNING" else "STOPPED"}")
        }
        
        /**
         * Demonstrate AlertGate cooldown mechanism
         */
        private fun demonstrateAlertGate() {
            Log.i(TAG, "--- Alert Gate Demo ---")
            
            // Test multiple rapid alert attempts
            for (i in 1..3) {
                if (AlertGate.tryOpen()) {
                    Log.i(TAG, "Alert $i: Gate opened successfully")
                    
                    // Simulate alert sending
                    Thread.sleep(100) 
                    
                    AlertGate.close()
                    Log.i(TAG, "Alert $i: Gate closed")
                } else {
                    Log.w(TAG, "Alert $i: Blocked - ${AlertGate.getStatus()}")
                }
                
                // Small delay between attempts
                Thread.sleep(500)
            }
        }
        
        /**
         * Demonstrate protection mode integration
         */
        private fun demonstrateProtectionModeIntegration(context: Context) {
            Log.i(TAG, "--- Protection Mode Integration Demo ---")
            
            // Test with protection mode OFF
            ProtectionMode.setArmed(context, false)
            Log.i(TAG, "Protection mode disabled - testing standard alert")
            
            // Test with protection mode ON
            ProtectionMode.setArmed(context, true)
            Log.w(TAG, "🛡️ Protection mode enabled - testing enhanced alert")
            
            // Reset to previous state (off for demo)
            ProtectionMode.setArmed(context, false)
        }
        
        /**
         * Simulate various hotword detection scenarios
         */
        private fun simulateHotwordScenarios(context: Context) {
            Log.i(TAG, "--- Hotword Simulation Demo ---")
            
            // Note: In real implementation, you'd get the service instance differently
            // This is just for demonstration
            
            // Scenario 1: Normal hotword detection
            Log.i(TAG, "Scenario 1: Normal hotword detection")
            // Simulate: service.onHotwordDetected("help", 0.85f)
            
            // Scenario 2: Multiple rapid detections (should be blocked by cooldown)
            Log.i(TAG, "Scenario 2: Rapid multiple detections")
            // Simulate: service.onHotwordDetected("emergency", 0.90f)
            // Simulate: service.onHotwordDetected("emergency", 0.92f) // Should be blocked
            
            // Scenario 3: Protection mode enabled detection
            Log.i(TAG, "Scenario 3: Protection mode detection")
            ProtectionMode.setArmed(context, true)
            
            // Wait for cooldown to reset (in real scenario, this would be 3 minutes)
            AlertGate.forceReset() // Only for demo - don't use in production
            
            // Simulate: service.onHotwordDetected("help", 0.95f)
            
            // Clean up
            ProtectionMode.setArmed(context, false)
        }
        
        /**
         * Show WorkManager queue status
         */
        private fun showWorkManagerStatus(context: Context) {
            Log.i(TAG, "--- WorkManager Status ---")
            
            try {
                val workManager = WorkManager.getInstance(context)
                
                // Get work info for our emergency alerts
                val workInfos = workManager.getWorkInfosByTag("emergency").get()
                
                Log.i(TAG, "Queued emergency work items: ${workInfos.size}")
                
                workInfos.forEach { workInfo ->
                    Log.i(TAG, "Work ${workInfo.id}: ${workInfo.state}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking WorkManager status", e)
            }
        }
        
        /**
         * Test thread safety of the alert system
         */
        fun testThreadSafety(context: Context, scope: CoroutineScope) {
            Log.i(TAG, "--- Thread Safety Test ---")
            
            // Launch multiple coroutines to test concurrent access
            repeat(5) { threadId ->
                scope.launch(Dispatchers.IO) {
                    val threadName = "AlertThread-$threadId"
                    
                    Log.d(TAG, "[$threadName] Starting alert attempt")
                    
                    if (AlertGate.tryOpen()) {
                        try {
                            Log.i(TAG, "[$threadName] ✅ Gate acquired")
                            
                            // Simulate alert work
                            delay(100)
                            
                            Log.i(TAG, "[$threadName] Alert work completed")
                            
                        } finally {
                            AlertGate.close()
                            Log.i(TAG, "[$threadName] Gate closed")
                        }
                    } else {
                        Log.w(TAG, "[$threadName] ❌ Gate blocked")
                    }
                }
            }
        }
        
        /**
         * Monitor alert system for a period of time
         */
        fun monitorAlertSystem(context: Context, scope: CoroutineScope, durationSeconds: Int = 30) {
            Log.i(TAG, "--- Alert System Monitoring (${durationSeconds}s) ---")
            
            scope.launch {
                val startTime = System.currentTimeMillis()
                val endTime = startTime + (durationSeconds * 1000)
                
                while (System.currentTimeMillis() < endTime) {
                    val stats = AlertGate.getStatistics()
                    val protectionArmed = ProtectionMode.isArmed(context)
                    val serviceRunning = WypeHotwordService.isRunning()
                    
                    Log.d(TAG, "Monitor: Gate=${stats["status"]}, Protection=$protectionArmed, Service=$serviceRunning")
                    
                    delay(5000) // Log every 5 seconds
                }
                
                Log.i(TAG, "Alert system monitoring completed")
            }
        }
    }
}

/*
 * ================================
 * USAGE EXAMPLES
 * ================================
 * 
 * Example usage from an Activity or Service:
 * ```kotlin
 * class MainActivity : AppCompatActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         
 *         // Demonstrate the complete alert system
 *         AlertSystemDemo.demonstrateCompleteAlertFlow(this)
 *         
 *         // Test thread safety
 *         AlertSystemDemo.testThreadSafety(this, lifecycleScope)
 *         
 *         // Monitor system for 60 seconds
 *         AlertSystemDemo.monitorAlertSystem(this, lifecycleScope, 60)
 *     }
 * }
 * ```
 * 
 * Example integration in a real hotword detection callback:
 * ```kotlin
 * class MyHotwordDetector {
 *     fun onHotwordDetected(word: String, confidence: Float) {
 *         // Get service instance (implementation dependent)
 *         val hotwordService = getHotwordServiceInstance()
 *         
 *         // This will automatically handle:
 *         // 1. Cooldown checking via AlertGate
 *         // 2. Protection mode status checking
 *         // 3. Reliable alert delivery via WorkManager
 *         // 4. No duplicate alerts via unique work names
 *         hotwordService.onHotwordDetected(word, confidence)
 *     }
 * }
 * ```
 * 
 * Example manual alert triggering:
 * ```kotlin
 * fun triggerEmergencyAlert(context: Context) {
 *     // Check if we can send an alert
 *     if (!AlertGate.isOpen()) {
 *         Log.w("Alert", "Cannot send alert: ${AlertGate.getStatus()}")
 *         return
 *     }
 *     
 *     // Create and enqueue alert worker
 *     val inputData = SendAlertWorker.createInputData(
 *         alertType = SendAlertWorker.ALERT_TYPE_PANIC,
 *         protectionMode = ProtectionMode.isArmed(context),
 *         message = "Manual emergency alert triggered"
 *     )
 *     
 *     val workRequest = OneTimeWorkRequestBuilder<SendAlertWorker>()
 *         .setInputData(inputData)
 *         .build()
 *     
 *     WorkManager.getInstance(context)
 *         .enqueueUniqueWork("manual_alert", ExistingWorkPolicy.REPLACE, workRequest)
 * }
 * ```
 */
