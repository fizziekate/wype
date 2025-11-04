package com.wype.security

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.firebase.FirebaseApp

/**
 * WYPE Application class with ultra-silent notification channel
 * for persistent hotword detection foreground service
 */
class WypeApp : Application() {

    companion object {
        // Silent notification channels for different services
        const val WYPE_SILENT_CHANNEL_ID = "wype_fg_silent"
        const val WYPE_PROTECTION_CHANNEL_ID = "wype_protection_silent" 
        const val WYPE_EMERGENCY_CHANNEL_ID = "wype_emergency_silent"
        
        // Channel names and descriptions
        const val SILENT_CHANNEL_NAME = "Wype Background"
        const val PROTECTION_CHANNEL_NAME = "Protection Mode"
        const val EMERGENCY_CHANNEL_NAME = "Emergency Services"
        
        const val SILENT_CHANNEL_DESC = "Silent background monitoring"
        const val PROTECTION_CHANNEL_DESC = "Silent protection mode monitoring"
        const val EMERGENCY_CHANNEL_DESC = "Emergency response services"
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Create all silent notification channels
        createSilentNotificationChannels()
    }

    /**
     * Creates multiple silent notification channels for different service types
     * Uses IMPORTANCE_MIN for maximum stealth and background operation
     */
    private fun createSilentNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Create background services channel (primary hotword detection)
            val silentChannel = NotificationChannel(
                WYPE_SILENT_CHANNEL_ID,
                SILENT_CHANNEL_NAME, 
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                setBypassDnd(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
                description = SILENT_CHANNEL_DESC
            }
            
            // Create protection mode channel (armed protection monitoring)
            val protectionChannel = NotificationChannel(
                WYPE_PROTECTION_CHANNEL_ID,
                PROTECTION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                setBypassDnd(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
                description = PROTECTION_CHANNEL_DESC
            }
            
            // Create emergency services channel (SMS and factory reset services)
            val emergencyChannel = NotificationChannel(
                WYPE_EMERGENCY_CHANNEL_ID,
                EMERGENCY_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                setBypassDnd(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
                description = EMERGENCY_CHANNEL_DESC
            }
            
            // Register all channels
            notificationManager.createNotificationChannels(listOf(
                silentChannel,
                protectionChannel, 
                emergencyChannel
            ))
        }
    }
}
