package com.wype.security.hotword

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Atomic alert gate with cooldown mechanism to prevent alert spam
 * 
 * Features:
 * - Thread-safe atomic lock using AtomicBoolean
 * - 3-minute cooldown period between alerts
 * - Single-fire mechanism - only one alert per cooldown period
 * - Automatic cooldown reset after timeout
 * - Non-blocking operations
 */
object AlertGate {
    
    private const val TAG = "AlertGate"
    
    // 3-minute cooldown in milliseconds
    private const val COOLDOWN_PERIOD_MS = 3 * 60 * 1000L // 180,000 ms
    
    // Atomic gate state - true when gate is open (can send alerts)
    private val gateOpen = AtomicBoolean(true)
    
    // Last alert timestamp for cooldown tracking
    private val lastAlertTime = AtomicLong(0L)
    
    /**
     * Attempt to open the alert gate for sending an alert
     * Thread-safe operation using atomic compare-and-swap
     * 
     * @return true if gate was successfully opened and alert can be sent,
     *         false if gate is closed (in cooldown) or already in use
     */
    fun tryOpen(): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastAlert = lastAlertTime.get()
        
        // Check if cooldown period has expired
        if (currentTime - lastAlert >= COOLDOWN_PERIOD_MS) {
            // Cooldown expired, try to open the gate atomically
            if (gateOpen.compareAndSet(true, false)) {
                // Successfully acquired the gate
                lastAlertTime.set(currentTime)
                Log.i(TAG, "Alert gate OPENED - Alert authorized")
                return true
            } else {
                Log.d(TAG, "Alert gate acquisition failed - Another thread got it first")
                return false
            }
        } else {
            // Still in cooldown period
            val remainingCooldown = COOLDOWN_PERIOD_MS - (currentTime - lastAlert)
            val remainingMinutes = remainingCooldown / (60 * 1000)
            val remainingSeconds = (remainingCooldown % (60 * 1000)) / 1000
            
            Log.w(TAG, "Alert BLOCKED - Cooldown active (${remainingMinutes}m ${remainingSeconds}s remaining)")
            return false
        }
    }
    
    /**
     * Close the alert gate after sending an alert
     * Starts the cooldown period
     */
    fun close() {
        if (gateOpen.compareAndSet(false, true)) {
            Log.i(TAG, "Alert gate CLOSED - Cooldown period started (3 minutes)")
        } else {
            Log.w(TAG, "Alert gate close called but gate was already closed")
        }
    }
    
    /**
     * Check if the gate is currently open (available for alerts)
     * Non-blocking read operation
     * 
     * @return true if gate is open and cooldown has expired, false otherwise
     */
    fun isOpen(): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastAlert = lastAlertTime.get()
        val isGateOpen = gateOpen.get()
        
        return isGateOpen && (currentTime - lastAlert >= COOLDOWN_PERIOD_MS)
    }
    
    /**
     * Get the remaining cooldown time in milliseconds
     * 
     * @return remaining cooldown time in ms, or 0 if no cooldown active
     */
    fun getRemainingCooldownMs(): Long {
        val currentTime = System.currentTimeMillis()
        val lastAlert = lastAlertTime.get()
        val elapsed = currentTime - lastAlert
        
        return if (elapsed < COOLDOWN_PERIOD_MS) {
            COOLDOWN_PERIOD_MS - elapsed
        } else {
            0L
        }
    }
    
    /**
     * Get human-readable cooldown status
     * 
     * @return status string with cooldown information
     */
    fun getStatus(): String {
        val remaining = getRemainingCooldownMs()
        val isGateOpen = gateOpen.get()
        
        return if (remaining > 0) {
            val minutes = remaining / (60 * 1000)
            val seconds = (remaining % (60 * 1000)) / 1000
            "COOLDOWN: ${minutes}m ${seconds}s remaining"
        } else if (isGateOpen) {
            "READY: Gate open, can send alert"
        } else {
            "BUSY: Gate acquired by another process"
        }
    }
    
    /**
     * Force reset the alert gate (for testing or emergency override)
     * USE WITH CAUTION - This bypasses the cooldown mechanism
     */
    fun forceReset() {
        gateOpen.set(true)
        lastAlertTime.set(0L)
        Log.w(TAG, "Alert gate FORCE RESET - Cooldown bypassed")
    }
    
    /**
     * Get detailed gate statistics for monitoring
     */
    fun getStatistics(): Map<String, Any> {
        val currentTime = System.currentTimeMillis()
        val lastAlert = lastAlertTime.get()
        val remaining = getRemainingCooldownMs()
        
        return mapOf(
            "isOpen" to isOpen(),
            "gateState" to gateOpen.get(),
            "lastAlertTime" to lastAlert,
            "currentTime" to currentTime,
            "remainingCooldownMs" to remaining,
            "cooldownPeriodMs" to COOLDOWN_PERIOD_MS,
            "status" to getStatus()
        )
    }
}

/*
 * ================================
 * USAGE EXAMPLES
 * ================================
 * 
 * Basic usage pattern:
 * ```kotlin
 * // Try to send an alert
 * if (AlertGate.tryOpen()) {
 *     try {
 *         // Send the alert (SMS, push notification, etc.)
 *         sendEmergencyAlert()
 *         Log.i("Alert", "Emergency alert sent successfully")
 *     } finally {
 *         // Always close the gate after sending
 *         AlertGate.close()
 *     }
 * } else {
 *     Log.w("Alert", "Alert blocked - ${AlertGate.getStatus()}")
 * }
 * ```
 * 
 * Status checking:
 * ```kotlin
 * // Check if ready to send
 * if (AlertGate.isOpen()) {
 *     Log.d("Alert", "Ready to send alert")
 * }
 * 
 * // Get detailed status
 * val status = AlertGate.getStatus()
 * Log.d("Alert", "Gate status: $status")
 * 
 * // Get remaining cooldown
 * val remaining = AlertGate.getRemainingCooldownMs()
 * if (remaining > 0) {
 *     Log.d("Alert", "Cooldown: ${remaining/1000}s remaining")
 * }
 * ```
 * 
 * Thread-safe usage from multiple threads:
 * ```kotlin
 * // Thread 1
 * Thread {
 *     if (AlertGate.tryOpen()) {
 *         // Only one thread will succeed
 *         sendAlert()
 *         AlertGate.close()
 *     }
 * }.start()
 * 
 * // Thread 2 (concurrent)
 * Thread {
 *     if (AlertGate.tryOpen()) {
 *         // This will fail if Thread 1 got the gate
 *         sendAlert()
 *         AlertGate.close()
 *     }
 * }.start()
 * ```
 * 
 * Error handling with automatic cleanup:
 * ```kotlin
 * fun sendAlertSafely() {
 *     if (!AlertGate.tryOpen()) {
 *         Log.w("Alert", "Cannot send alert: ${AlertGate.getStatus()}")
 *         return
 *     }
 *     
 *     try {
 *         // Attempt to send alert
 *         performAlertOperation()
 *     } catch (e: Exception) {
 *         Log.e("Alert", "Alert sending failed", e)
 *         // Gate will still be closed in finally block
 *     } finally {
 *         // Ensure gate is always closed
 *         AlertGate.close()
 *     }
 * }
 * ```
 */
