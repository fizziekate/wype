package com.wype.security.hotword

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * Thread-safe Protection Mode flag management using DataStore Preferences
 * 
 * Provides persistent storage for the protection mode armed state with:
 * - Thread-safe operations using DataStore
 * - Coroutine-based async operations with blocking fallbacks
 * - Automatic data persistence across app restarts
 * - Type-safe boolean storage
 */
object ProtectionMode {
    
    // DataStore instance using extension property
    private val Context.protectionModeDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "protection_mode_prefs"
    )
    
    // Preference key for the protection mode armed state
    private val PROTECTION_ARMED_KEY = booleanPreferencesKey("protection_armed")
    
    /**
     * Set the protection mode armed state
     * Thread-safe operation that persists the state to DataStore
     * 
     * @param context Application or Activity context
     * @param isArmed Boolean flag indicating if protection mode should be armed
     */
    fun setArmed(context: Context, isArmed: Boolean) {
        runBlocking {
            context.protectionModeDataStore.edit { preferences ->
                preferences[PROTECTION_ARMED_KEY] = isArmed
            }
        }
    }
    
    /**
     * Get the current protection mode armed state
     * Thread-safe operation that reads from DataStore
     * 
     * @param context Application or Activity context
     * @return Boolean indicating if protection mode is currently armed (defaults to false)
     */
    fun isArmed(context: Context): Boolean {
        return runBlocking {
            context.protectionModeDataStore.data
                .map { preferences ->
                    preferences[PROTECTION_ARMED_KEY] ?: false // Default to false if not set
                }
                .first()
        }
    }
    
    /**
     * Suspend function version of setArmed for use in coroutines
     * More efficient when already in a coroutine context
     * 
     * @param context Application or Activity context
     * @param isArmed Boolean flag indicating if protection mode should be armed
     */
    suspend fun setArmedSuspend(context: Context, isArmed: Boolean) {
        context.protectionModeDataStore.edit { preferences ->
            preferences[PROTECTION_ARMED_KEY] = isArmed
        }
    }
    
    /**
     * Suspend function version of isArmed for use in coroutines
     * More efficient when already in a coroutine context
     * 
     * @param context Application or Activity context
     * @return Boolean indicating if protection mode is currently armed
     */
    suspend fun isArmedSuspend(context: Context): Boolean {
        return context.protectionModeDataStore.data
            .map { preferences ->
                preferences[PROTECTION_ARMED_KEY] ?: false
            }
            .first()
    }
    
    /**
     * Get a Flow of the protection mode state for reactive UI updates
     * Useful for observing state changes in real-time
     * 
     * @param context Application or Activity context
     * @return Flow<Boolean> that emits whenever the protection mode state changes
     */
    fun getArmedStateFlow(context: Context) = 
        context.protectionModeDataStore.data.map { preferences ->
            preferences[PROTECTION_ARMED_KEY] ?: false
        }
    
    /**
     * Clear all protection mode preferences (for testing or reset purposes)
     * 
     * @param context Application or Activity context
     */
    suspend fun clearPreferences(context: Context) {
        context.protectionModeDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

/*
 * ================================
 * EXAMPLE USAGE SNIPPETS
 * ================================
 * 
 * 1. Basic synchronous usage (blocks current thread):
 * ```kotlin
 * // Arm protection mode
 * ProtectionMode.setArmed(context, true)
 * 
 * // Check if armed
 * val isArmed = ProtectionMode.isArmed(context)
 * if (isArmed) {
 *     Log.d("Protection", "Protection mode is ARMED")
 * }
 * 
 * // Disarm protection mode
 * ProtectionMode.setArmed(context, false)
 * ```
 * 
 * 2. Coroutine-based usage (more efficient in suspend contexts):
 * ```kotlin
 * class MyService : Service() {
 *     private fun startProtection() {
 *         lifecycleScope.launch {
 *             // Arm protection mode
 *             ProtectionMode.setArmedSuspend(this@MyService, true)
 *             
 *             // Check state
 *             val isArmed = ProtectionMode.isArmedSuspend(this@MyService)
 *             Log.d("Protection", "Armed state: $isArmed")
 *         }
 *     }
 * }
 * ```
 * 
 * 3. Reactive state observation:
 * ```kotlin
 * class MainActivity : AppCompatActivity() {
 *     private fun observeProtectionState() {
 *         lifecycleScope.launch {
 *             ProtectionMode.getArmedStateFlow(this@MainActivity).collect { isArmed ->
 *                 updateUI(isArmed)
 *                 Log.d("Protection", "State changed: $isArmed")
 *             }
 *         }
 *     }
 * }
 * ```
 * 
 * 4. Integration with WypeHotwordService:
 * ```kotlin
 * class WypeHotwordService : Service() {
 *     private fun checkProtectionMode() {
 *         val isArmed = ProtectionMode.isArmed(this)
 *         if (isArmed) {
 *             // Start enhanced protection monitoring
 *             startEnhancedMonitoring()
 *         } else {
 *             // Standard hotword detection only
 *             startStandardMonitoring()
 *         }
 *     }
 *     
 *     private fun armProtectionMode() {
 *         ProtectionMode.setArmed(this, true)
 *         Log.i("Protection", "Protection mode ARMED")
 *         // Restart service with new configuration
 *         restartWithProtectionMode()
 *     }
 * }
 * ```
 * 
 * 5. Thread-safe usage from multiple contexts:
 * ```kotlin
 * // Safe to call from any thread
 * Thread {
 *     ProtectionMode.setArmed(applicationContext, true)
 *     val isArmed = ProtectionMode.isArmed(applicationContext)
 * }.start()
 * 
 * // Safe to call from UI thread
 * runOnUiThread {
 *     val isArmed = ProtectionMode.isArmed(this)
 *     updateProtectionIndicator(isArmed)
 * }
 * ```
 */
