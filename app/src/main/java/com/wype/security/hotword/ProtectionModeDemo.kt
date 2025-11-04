package com.wype.security.hotword

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Demonstration class showing how to use ProtectionMode
 * This class provides practical examples of the ProtectionMode API
 */
class ProtectionModeDemo {

    companion object {
        private const val TAG = "ProtectionModeDemo"
        
        /**
         * Demonstrate basic Protection Mode operations
         */
        fun demonstrateBasicOperations(context: Context) {
            Log.d(TAG, "=== Basic Protection Mode Demo ===")
            
            // Check initial state
            val initialState = ProtectionMode.isArmed(context)
            Log.d(TAG, "Initial protection state: $initialState")
            
            // Arm protection mode
            ProtectionMode.setArmed(context, true)
            Log.d(TAG, "Protection mode ARMED")
            
            // Verify it's armed
            val armedState = ProtectionMode.isArmed(context)
            Log.d(TAG, "Protection state after arming: $armedState")
            
            // Disarm protection mode
            ProtectionMode.setArmed(context, false)
            Log.d(TAG, "Protection mode DISARMED")
            
            // Verify it's disarmed
            val disarmedState = ProtectionMode.isArmed(context)
            Log.d(TAG, "Protection state after disarming: $disarmedState")
        }
        
        /**
         * Demonstrate coroutine-based operations
         */
        fun demonstrateCoroutineOperations(context: Context, scope: CoroutineScope) {
            Log.d(TAG, "=== Coroutine Protection Mode Demo ===")
            
            scope.launch {
                try {
                    // Check initial state (suspend)
                    val initialState = ProtectionMode.isArmedSuspend(context)
                    Log.d(TAG, "Initial state (suspend): $initialState")
                    
                    // Arm protection mode (suspend)
                    ProtectionMode.setArmedSuspend(context, true)
                    Log.d(TAG, "Armed using suspend function")
                    
                    // Verify armed state
                    val armedState = ProtectionMode.isArmedSuspend(context)
                    Log.d(TAG, "Armed state verified: $armedState")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in coroutine demo", e)
                }
            }
        }
        
        /**
         * Demonstrate state flow observation
         */
        fun demonstrateStateObservation(context: Context, scope: CoroutineScope) {
            Log.d(TAG, "=== State Flow Protection Mode Demo ===")
            
            scope.launch {
                try {
                    // Observe protection state changes
                    ProtectionMode.getArmedStateFlow(context).collect { isArmed ->
                        Log.d(TAG, "Protection state changed: $isArmed")
                        
                        if (isArmed) {
                            Log.i(TAG, "🛡️ PROTECTION MODE ACTIVATED")
                        } else {
                            Log.i(TAG, "🔓 Protection mode deactivated")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error observing state", e)
                }
            }
        }
        
        /**
         * Demonstrate thread safety by calling from different threads
         */
        fun demonstrateThreadSafety(context: Context) {
            Log.d(TAG, "=== Thread Safety Demo ===")
            
            // Thread 1: Set armed state
            Thread {
                Thread.currentThread().name = "ProtectionThread-1"
                Log.d(TAG, "[${Thread.currentThread().name}] Setting armed to true")
                ProtectionMode.setArmed(context, true)
                
                val state1 = ProtectionMode.isArmed(context)
                Log.d(TAG, "[${Thread.currentThread().name}] State: $state1")
            }.start()
            
            // Thread 2: Check and modify state
            Thread {
                Thread.currentThread().name = "ProtectionThread-2"
                Thread.sleep(100) // Small delay
                
                val state2 = ProtectionMode.isArmed(context)
                Log.d(TAG, "[${Thread.currentThread().name}] Read state: $state2")
                
                ProtectionMode.setArmed(context, false)
                Log.d(TAG, "[${Thread.currentThread().name}] Set armed to false")
            }.start()
            
            // Thread 3: Final check
            Thread {
                Thread.currentThread().name = "ProtectionThread-3"
                Thread.sleep(200) // Longer delay
                
                val finalState = ProtectionMode.isArmed(context)
                Log.d(TAG, "[${Thread.currentThread().name}] Final state: $finalState")
            }.start()
        }
    }
}

/**
 * Example service integration showing how to use ProtectionMode with WypeHotwordService
 */
class ProtectionModeServiceExample {
    
    fun checkAndStartProtection(context: Context) {
        val isArmed = ProtectionMode.isArmed(context)
        
        if (isArmed) {
            Log.i("ServiceExample", "Protection mode is ARMED - starting enhanced monitoring")
            // Start WypeHotwordService with protection mode enabled
            HotwordServiceHelper.startHotwordService(context)
        } else {
            Log.i("ServiceExample", "Protection mode is OFF - standard operation")
        }
    }
    
    fun armProtectionAndStartService(context: Context) {
        // Arm protection mode
        ProtectionMode.setArmed(context, true)
        Log.w("ServiceExample", "🛡️ PROTECTION MODE ARMED")
        
        // Start the hotword service
        HotwordServiceHelper.startHotwordService(context)
        Log.i("ServiceExample", "Silent hotword service started with protection active")
    }
    
    fun disarmProtectionAndStopService(context: Context) {
        // Disarm protection mode
        ProtectionMode.setArmed(context, false)
        Log.i("ServiceExample", "🔓 Protection mode DISARMED")
        
        // Optionally stop the service or reconfigure it
        HotwordServiceHelper.stopHotwordService(context)
        Log.i("ServiceExample", "Hotword service stopped")
    }
}

/*
 * Usage examples from different Android components:
 * 
 * // In an Activity:
 * class MainActivity : AppCompatActivity() {
 *     private fun setupProtection() {
 *         ProtectionModeDemo.demonstrateBasicOperations(this)
 *         ProtectionModeDemo.demonstrateCoroutineOperations(this, lifecycleScope)
 *     }
 * }
 * 
 * // In a Service:
 * class MyService : Service() {
 *     override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
 *         val isProtected = ProtectionMode.isArmed(this)
 *         if (isProtected) {
 *             Log.i("MyService", "Running in protection mode")
 *         }
 *         return START_STICKY
 *     }
 * }
 * 
 * // In a Fragment:
 * class SettingsFragment : Fragment() {
 *     private fun toggleProtection() {
 *         val currentState = ProtectionMode.isArmed(requireContext())
 *         ProtectionMode.setArmed(requireContext(), !currentState)
 *     }
 * }
 */
