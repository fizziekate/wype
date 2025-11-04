package com.wype.security

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.firebase.FirebaseApp

class WypeApplication : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "WYPE_SERVICE_CHANNEL"
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN // Changed to MIN for minimal visibility
            ).apply {
                description = getString(R.string.notification_channel_description)
                // Hide from lock screen to be less intrusive
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
                // Don't show badge
                setShowBadge(false)
                // Don't bypass Do Not Disturb
                setBypassDnd(false)
                // Disable vibration and sound for silent operation
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
