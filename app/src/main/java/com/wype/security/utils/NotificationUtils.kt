package com.wype.security.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wype.security.R

object NotificationUtils {

    // Bump the ID if you ever created an older noisy channel.
    // Changing this forces Android to create a fresh (silent) channel.
    const val CHANNEL_ID = "wype_bg_service_v2"

    private const val CHANNEL_NAME = "Wype Protection Mode"
    private const val CHANNEL_DESC = "Wype is running in the background to detect emergency triggers."

    /**
     * Create or update a silent notification channel.
     * Note: On Android 8+, some channel settings can be sticky once created by the OS/user.
     * This method applies best-effort silent settings. If you ever had a noisy channel,
     * bump CHANNEL_ID to force a new one.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = CHANNEL_DESC

            // Silent channel: no sound/vibration/lights
            setSound(null, null)
            enableVibration(false)
            vibrationPattern = longArrayOf(0)
            enableLights(false)
            setShowBadge(false)

            // Keep it discreet on lockscreen
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }

        // Creates or updates the channel (best effort)
        nm.createNotificationChannel(channel)
    }

    /**
     * Foreground notification used by HotwordService / ProtectionModeService.
     * Keep it silent and ongoing.
     */
    fun buildForeground(context: Context): Notification {
        ensureChannel(context)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_wype) // <- create this drawable
            .setContentTitle("Wype is active")
            .setContentText("Offline emergency phrase detection is running")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Optional: if you ever want to show a one-off silent status update notification.
     */
    fun buildStatus(context: Context, title: String, text: String): Notification {
        ensureChannel(context)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_wype)
            .setContentTitle(title)
            .setContentText(text)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
