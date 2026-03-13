package com.wype.security.service

import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.wype.security.admin.WypeDeviceAdminReceiver
import com.wype.security.utils.NotificationUtils
import com.wype.security.utils.PreferencesManager

/**
 * Offline HotwordService (NO SpeechRecognizer, NO beeping)
 *
 * Uses Porcupine offline wake-word detection in a Foreground Service.
 * Listens for the recorded phrase twice within 10 seconds to trigger emergency actions.
 */
class HotwordService : Service() {

    companion object {
        private const val TAG = "HotwordService"
        private const val NOTIFICATION_ID = 2001
        private const val CONFIRMATION_TIMEOUT_MS = 10000L // 10 seconds

        @Volatile
        var running = false
    }

    private lateinit var preferencesManager: PreferencesManager
    private var isServiceActive = false

    private var porcupineManager: PorcupineManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Timing state for double-trigger logic
    private var firstWakeWordTime = 0L
    private var wakeWordCount = 0
    private var lastDetectionTime = 0L

    private val picovoiceAccessKey: String
        get() = preferencesManager.getPorcupineAccessKey() ?: "PUT_YOUR_PICOVOICE_ACCESS_KEY_HERE"

    private val keywordAssetPath = "wype.ppn"
    private val sensitivity = 0.65f

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🎧 HotwordService created (offline / no SpeechRecognizer)")

        if (running) {
            Log.w(TAG, "Service already running - stopping duplicate")
            stopSelf()
            return
        }

        running = true
        preferencesManager = PreferencesManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isServiceActive) {
            Log.d(TAG, "Service already active")
            return START_STICKY
        }

        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing RECORD_AUDIO permission - cannot start hotword")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            // Foreground notification (Silent/Standard)
            NotificationUtils.ensureChannel(this)
            startForeground(NOTIFICATION_ID, NotificationUtils.buildForeground(this))

            acquireWakeLock()
            startOfflineHotword()

            isServiceActive = true
            Log.i(TAG, "✅ HotwordService started (offline wake word active)")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting HotwordService", e)
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "🧹 HotwordService destroyed")

        try {
            stopOfflineHotword()
            releaseWakeLock()
        } catch (e: Exception) {
            Log.e(TAG, "Error during service cleanup", e)
        } finally {
            isServiceActive = false
            running = false
            wakeWordCount = 0
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startOfflineHotword() {
        Log.i(TAG, "🔇 Starting OFFLINE wake word detection (Porcupine)")

        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(picovoiceAccessKey)
                .setKeywordPath(keywordAssetPath)
                .setSensitivity(sensitivity)
                .build(applicationContext) {
                    onWakeWordDetected()
                }

            porcupineManager?.start()

        } catch (e: PorcupineException) {
            Log.e(TAG, "Porcupine init failed (key/asset/mic).", e)
            stopSelf()
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected error starting Porcupine", t)
            stopSelf()
        }
    }

    private fun stopOfflineHotword() {
        try { porcupineManager?.stop() } catch (_: Exception) {}
        try { porcupineManager?.delete() } catch (_: Exception) {}
        porcupineManager = null
    }

    private fun onWakeWordDetected() {
        val currentTime = System.currentTimeMillis()
        
        // Debounce detections (2s)
        if (currentTime - lastDetectionTime < 2000) return
        lastDetectionTime = currentTime

        if (wakeWordCount == 0 || (currentTime - firstWakeWordTime) > CONFIRMATION_TIMEOUT_MS) {
            // First detection or timeout exceeded: Start new window
            firstWakeWordTime = currentTime
            wakeWordCount = 1
            Log.w(TAG, "🚨 First detection! Repeat phrase within 10s to trigger WYPE.")
            preferencesManager.logPhraseDetection("Hotword: First detection (Awaiting confirmation)", currentTime)
        } else {
            // Second detection within 10 seconds
            wakeWordCount = 0 
            Log.w(TAG, "🚨 DOUBLE TRIGGER DETECTED! Initiating emergency sequence.")
            
            // 1) Lock device immediately
            WypeDeviceAdminReceiver.lockDevice(this)

            // 2) Trigger silent emergency actions: SMS -> Backup -> Factory Reset (no prompts)
            ProtectionModeManager.getInstance(this).triggerEmergencyActions()
            
            preferencesManager.logPhraseDetection("Hotword: Double-trigger CONFIRMED. Sequence started.", currentTime)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Wype:HotwordLock").apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L) // 30 min timeout
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }
}
