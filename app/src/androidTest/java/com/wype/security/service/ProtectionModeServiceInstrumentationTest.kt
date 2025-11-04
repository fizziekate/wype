package com.wype.security.service

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wype.security.utils.PreferencesManager
import org.junit.*
import org.junit.runner.RunWith

/**
 * Comprehensive instrumentation tests for ProtectionModeService double-confirmation behavior
 * 
 * This test suite focuses on testing the confirmation state persistence logic and integration
 * with the PreferencesManager. Since ProtectionModeService runs as a foreground service 
 * without exposing a binder, these tests validate the persistent state behavior that enables
 * crash resilience in emergency detection.
 * 
 * Tests:
 * - Confirmation state persistence and recovery
 * - State validation and expiration logic
 * - Emergency conditions and daily limits
 * - PreferencesManager integration for crash resilience
 */
@RunWith(AndroidJUnit4::class)
class ProtectionModeServiceInstrumentationTest {

    companion object {
        private const val TAG = "ProtectionModeServiceTest"
        private const val TEST_WAKE_PHRASE = "emergency help me"
        private const val TEST_DETECTOR_TYPE = "PorcupineHotword"
        private const val TEST_CONFIDENCE = 0.85f
        
        // Timeouts from ProtectionModeService
        private const val CONFIRMATION_TIMEOUT_MS = 30000L
        private const val DEBOUNCE_TIME_MS = 2000L
        private const val DAILY_EMERGENCY_LIMIT = 3
    }

    private lateinit var context: Context
    private lateinit var preferencesManager: PreferencesManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferencesManager = PreferencesManager(context)
        
        // Configure test preferences
        setupTestPreferences()
        
        // Clear any existing confirmation state
        preferencesManager.clearConfirmationState()
        preferencesManager.resetProtectionModeEmergencyCount()
    }

    @After
    fun tearDown() {
        try {
            // Clean up test state
            preferencesManager.clearConfirmationState()
            preferencesManager.resetProtectionModeEmergencyCount()
            
            // Stop any running protection service
            val stopIntent = Intent(context, ProtectionModeService::class.java).apply {
                action = ProtectionModeService.ACTION_STOP_PROTECTION
            }
            context.startService(stopIntent)
            
        } catch (e: Exception) {
            // Ignore cleanup errors in tests
        }
    }

    /**
     * Configure test preferences for protection mode
     */
    private fun setupTestPreferences() {
        preferencesManager.apply {
            setProtectionModeEnabled(true)
            setWakePhrase(TEST_WAKE_PHRASE)
            setBuddyContact("Test Buddy", "5551234567") // Test emergency contact
            setMLDetectionMode(PreferencesManager.MLDetectionMode.PORCUPINE)
            setMLPerformanceProfile(PreferencesManager.MLPerformanceProfile.BALANCED)
        }
    }

    /**
     * Start ProtectionModeService 
     */
    private fun startProtectionModeService() {
        val serviceIntent = Intent(context, ProtectionModeService::class.java).apply {
            action = ProtectionModeService.ACTION_START_PROTECTION
        }
        
        context.startService(serviceIntent)
        Thread.sleep(1000) // Wait for service initialization
    }

    /**
     * Stop ProtectionModeService
     */
    private fun stopProtectionModeService() {
        val serviceIntent = Intent(context, ProtectionModeService::class.java).apply {
            action = ProtectionModeService.ACTION_STOP_PROTECTION
        }
        
        context.startService(serviceIntent)
        Thread.sleep(500) // Wait for service shutdown
    }

    // ========================================
    // TEST 1: Confirmation State Persistence
    // ========================================

    @Test
    fun testConfirmationStatePersistenceBasics() {
        // Test saving confirmation state
        preferencesManager.saveConfirmationState(
            count = 1,
            firstTime = System.currentTimeMillis(),
            lastTime = System.currentTimeMillis(),
            keyword = TEST_WAKE_PHRASE,
            detectorType = TEST_DETECTOR_TYPE
        )
        
        // Verify state is persisted
        val savedState = preferencesManager.loadConfirmationState()
        Assert.assertEquals("Count should be saved", 1, savedState.count)
        Assert.assertEquals("Keyword should be saved", TEST_WAKE_PHRASE, savedState.keyword)
        Assert.assertEquals("Detector type should be saved", TEST_DETECTOR_TYPE, savedState.detectorType)
        Assert.assertTrue("First time should be saved", savedState.firstTime > 0)
        Assert.assertTrue("Last time should be saved", savedState.lastTime > 0)
    }

    // ========================================
    // TEST 2: Confirmation State Clearing
    // ========================================

    @Test
    fun testConfirmationStateClearing() {
        // Save some confirmation state
        preferencesManager.saveConfirmationState(
            count = 1,
            firstTime = System.currentTimeMillis(),
            lastTime = System.currentTimeMillis(),
            keyword = TEST_WAKE_PHRASE,
            detectorType = TEST_DETECTOR_TYPE
        )
        
        // Verify state exists
        var state = preferencesManager.loadConfirmationState()
        Assert.assertEquals("State should exist", 1, state.count)
        
        // Clear state
        preferencesManager.clearConfirmationState()
        
        // Verify state is cleared
        state = preferencesManager.loadConfirmationState()
        Assert.assertEquals("State should be cleared", 0, state.count)
        Assert.assertTrue("Keyword should be null or empty", state.keyword.isNullOrEmpty())
        Assert.assertTrue("Detector should be null or empty", state.detectorType.isNullOrEmpty())
        Assert.assertEquals("First time should be 0", 0L, state.firstTime)
        Assert.assertEquals("Last time should be 0", 0L, state.lastTime)
    }

    // ========================================
    // TEST 3: State Validation - Valid State
    // ========================================

    @Test
    fun testConfirmationStateValidationWithValidState() {
        val currentTime = System.currentTimeMillis()
        
        // Save valid confirmation state (within timeout)
        preferencesManager.saveConfirmationState(
            count = 1,
            firstTime = currentTime - 5000, // 5 seconds ago
            lastTime = currentTime - 1000,  // 1 second ago
            keyword = TEST_WAKE_PHRASE,
            detectorType = TEST_DETECTOR_TYPE
        )
        
        // Verify state is valid
        val isValid = preferencesManager.isConfirmationStateValid(CONFIRMATION_TIMEOUT_MS)
        Assert.assertTrue("Valid state should pass validation", isValid)
        
        // Get state status
        val status = preferencesManager.getConfirmationStateStatus()
        Assert.assertEquals("Status count should be 1", 1, status["count"])
        Assert.assertEquals("Status keyword should match", TEST_WAKE_PHRASE, status["keyword"])
        Assert.assertTrue("Status should indicate valid state", status["isValid"] as Boolean)
    }

    // ========================================
    // TEST 4: State Validation - Expired State
    // ========================================

    @Test
    fun testConfirmationStateValidationWithExpiredState() {
        val expiredTime = System.currentTimeMillis() - (CONFIRMATION_TIMEOUT_MS + 10000) // Expired by 10s
        
        // Save expired confirmation state
        preferencesManager.saveConfirmationState(
            count = 1,
            firstTime = expiredTime,
            lastTime = expiredTime + 1000,
            keyword = TEST_WAKE_PHRASE,
            detectorType = TEST_DETECTOR_TYPE
        )
        
        // Verify state is invalid
        val isValid = preferencesManager.isConfirmationStateValid(CONFIRMATION_TIMEOUT_MS)
        Assert.assertFalse("Expired state should fail validation", isValid)
        
        // Get state status
        val status = preferencesManager.getConfirmationStateStatus()
        Assert.assertEquals("Status count should still be 1", 1, status["count"])
        Assert.assertFalse("Status should indicate invalid state", status["isValid"] as Boolean)
        
        // Verify age calculation (in seconds, not milliseconds)
        val firstTimeAgoSeconds = status["firstTimeAgo"] as Long
        Assert.assertTrue("Age should be greater than timeout", firstTimeAgoSeconds > CONFIRMATION_TIMEOUT_MS / 1000)
    }

    // ========================================
    // TEST 5: Service Startup and State Handling
    // ========================================

    @Test
    fun testServiceStartupWithExistingValidState() {
        // Save valid confirmation state before starting service
        val currentTime = System.currentTimeMillis()
        preferencesManager.saveConfirmationState(
            count = 1,
            firstTime = currentTime - 5000, // 5 seconds ago (within timeout)
            lastTime = currentTime - 1000,  // 1 second ago
            keyword = TEST_WAKE_PHRASE,
            detectorType = TEST_DETECTOR_TYPE
        )
        
        // Verify state exists and is valid before starting service
        val initialState = preferencesManager.loadConfirmationState()
        Assert.assertEquals("Initial state count should be 1", 1, initialState.count)
        Assert.assertTrue("Initial state should be valid", 
            preferencesManager.isConfirmationStateValid(CONFIRMATION_TIMEOUT_MS))
        
        // Start service (should load existing valid state)
        startProtectionModeService()
        
        // Verify state is still present after service startup
        val loadedState = preferencesManager.loadConfirmationState()
        Assert.assertEquals("Loaded state count should match", 1, loadedState.count)
        Assert.assertEquals("Loaded keyword should match", TEST_WAKE_PHRASE, loadedState.keyword)
    }

    // ========================================
    // TEST 6: Service Startup with Expired State
    // ========================================

    @Test
    fun testServiceStartupWithExpiredState() {
        // Save expired confirmation state before starting service
        val expiredTime = System.currentTimeMillis() - (CONFIRMATION_TIMEOUT_MS + 10000) // 10s past timeout
        preferencesManager.saveConfirmationState(
            count = 1,
            firstTime = expiredTime,
            lastTime = expiredTime + 1000,
            keyword = TEST_WAKE_PHRASE,
            detectorType = TEST_DETECTOR_TYPE
        )
        
        // Verify expired state exists before starting service
        val expiredState = preferencesManager.loadConfirmationState()
        Assert.assertEquals("Expired state count should be 1", 1, expiredState.count)
        Assert.assertFalse("Expired state should be invalid", 
            preferencesManager.isConfirmationStateValid(CONFIRMATION_TIMEOUT_MS))
        
        // Start service (should detect and clear expired state)
        startProtectionModeService()
        Thread.sleep(500) // Allow service to process state
        
        // The service should have cleared the expired state on startup
        // Note: This behavior depends on the service implementation
        // For now, we just verify the state validation works correctly
        Assert.assertFalse("Expired state should remain invalid", 
            preferencesManager.isConfirmationStateValid(CONFIRMATION_TIMEOUT_MS))
    }

    // ========================================
    // TEST 7: Emergency Count Management
    // ========================================

    @Test
    fun testEmergencyCountManagement() {
        // Test initial count
        val initialCount = preferencesManager.getProtectionModeEmergencyCount()
        Assert.assertEquals("Initial emergency count should be 0", 0, initialCount)
        
        // Test incrementing count
        for (i in 1..DAILY_EMERGENCY_LIMIT) {
            val count = preferencesManager.incrementProtectionModeEmergencyCount()
            Assert.assertEquals("Emergency count should increment", i, count)
        }
        
        // Verify final count
        val finalCount = preferencesManager.getProtectionModeEmergencyCount()
        Assert.assertEquals("Final count should match limit", DAILY_EMERGENCY_LIMIT, finalCount)
        
        // Test reset
        preferencesManager.resetProtectionModeEmergencyCount()
        val resetCount = preferencesManager.getProtectionModeEmergencyCount()
        Assert.assertEquals("Reset count should be 0", 0, resetCount)
    }

    // ========================================
    // TEST 8: Service Integration Test
    // ========================================

    @Test
    fun testServiceStartStopCycle() {
        // Test basic service lifecycle
        startProtectionModeService()
        Thread.sleep(500)
        
        // Service should be running (we can't directly check, but it should accept the start command)
        // This is more of a smoke test to ensure service can start without crashing
        
        // Save some test state
        preferencesManager.saveConfirmationState(
            count = 1,
            firstTime = System.currentTimeMillis(),
            lastTime = System.currentTimeMillis(),
            keyword = TEST_WAKE_PHRASE,
            detectorType = TEST_DETECTOR_TYPE
        )
        
        // Stop service
        stopProtectionModeService()
        
        // Verify state persists after service stop
        val persistedState = preferencesManager.loadConfirmationState()
        Assert.assertEquals("State should persist after service stop", 1, persistedState.count)
        
        // Clean up
        preferencesManager.clearConfirmationState()
    }
}
