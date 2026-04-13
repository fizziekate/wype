package com.wype.security.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ActivityCompat
import com.wype.security.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service to handle emergency SMS sending
 */
class EmergencySmsService : Service() {

    companion object {
        private const val TAG = "EmergencySmsService"
        const val ACTION_SEND_EMERGENCY_SMS = "SEND_EMERGENCY_SMS"
        const val ACTION_SMS_SENT = "SMS_SENT"
        const val ACTION_SMS_DELIVERED = "SMS_DELIVERED"
        private const val SMS_SENT_REQUEST_CODE = 1001
        private const val SMS_DELIVERED_REQUEST_CODE = 1002
        private const val NOTIFICATION_ID = 2001
    }

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var twilioSmsService: TwilioSmsService
    private var sentReceiver: BroadcastReceiver? = null
    private var deliveredReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        twilioSmsService = TwilioSmsService(this)
        registerSmsReceivers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SEND_EMERGENCY_SMS -> {
                // Start as foreground service for critical emergency operation
                // Android 14+ requires the type to be passed explicitly in code too
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, createEmergencyNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
                } else {
                    startForeground(NOTIFICATION_ID, createEmergencyNotification())
                }
                sendEmergencySms()
            }
        }
        
        // Stop self after handling the request
        stopSelf()
        return START_NOT_STICKY
    }

    private fun sendEmergencySms() {
        Log.w(TAG, "Emergency SMS sending initiated")

        val currentTime = System.currentTimeMillis()

        // Check if we have SMS permission
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "SMS permission not granted - cannot send emergency SMS")
            return
        }
        
        // Get buddy contact
        val buddyName = preferencesManager.getBuddyName()
        val buddyPhone = preferencesManager.getBuddyPhone()
        
        if (buddyName.isNullOrEmpty() || buddyPhone.isNullOrEmpty()) {
            Log.e(TAG, "No buddy contact configured - cannot send emergency SMS")
            return
        }
        
        // Update last SMS time BEFORE sending to prevent rapid duplicates
        preferencesManager.setLastEmergencySmsTime(currentTime)
        
        // Create emergency message
        val emergencyMessage = createEmergencyMessage()
        
        // Try Twilio first, then fallback to local SMS
        sendSmsWithTwilioFallback(buddyPhone, emergencyMessage, buddyName)
    }
    
    private fun createEmergencyMessage(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())

        return buildString {
            append("EMERGENCY ALERT\n")
            append("WYPE security app detected emergency phrase.\n")
            append("Time: $timestamp\n")
            append("Device security measures have been activated.")
        }
    }
    
    /**
     * Send SMS using Twilio as primary option, fallback to local SMS
     */
    private fun sendSmsWithTwilioFallback(phoneNumber: String, message: String, buddyName: String) {
        val timestamp = System.currentTimeMillis()
        
        // Check if Twilio is configured and enabled
        if (preferencesManager.isTwilioEnabled() && twilioSmsService.isTwilioConfigured()) {
            Log.i(TAG, "Attempting to send emergency SMS via Twilio")
            
            // Format phone number for Twilio
            val formattedPhone = twilioSmsService.formatPhoneNumber(phoneNumber)
            
            twilioSmsService.sendSms(
                toPhoneNumber = formattedPhone,
                messageBody = message,
                onSuccess = {
                    Log.w(TAG, "Emergency SMS sent via Twilio to $buddyName at $phoneNumber")
                    preferencesManager.logPhraseDetection("EMERGENCY SMS SENT (Twilio) to $buddyName", timestamp)
                },
                onError = { error ->
                    Log.w(TAG, "Twilio SMS failed, falling back to local SMS: ${error.message}")
                    // Fallback to local SMS
                    sendLocalSms(phoneNumber, message, buddyName, timestamp)
                }
            )
        } else {
            Log.i(TAG, "Twilio not configured, using local SMS")
            // Use local SMS directly
            sendLocalSms(phoneNumber, message, buddyName, timestamp)
        }
    }
    
    /**
     * Send SMS using local Android SmsManager
     */
    private fun sendLocalSms(phoneNumber: String, message: String, buddyName: String, timestamp: Long) {
        try {
            sendSmsWithDeliveryConfirmation(phoneNumber, message)
            
            Log.w(TAG, "Emergency SMS sent via local SMS to $buddyName at $phoneNumber")
            
            // Log the emergency event
            preferencesManager.logPhraseDetection("EMERGENCY SMS SENT (Local) to $buddyName", timestamp)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send local emergency SMS", e)
            preferencesManager.logPhraseDetection("EMERGENCY SMS FAILED to $buddyName: ${e.message}", timestamp)
        }
    }
    
    private fun sendSmsWithDeliveryConfirmation(phoneNumber: String, message: String) {
        val smsManager = SmsManager.getDefault()
        
        // Create pending intents for delivery confirmation
        val sentPendingIntent = PendingIntent.getBroadcast(
            this, SMS_SENT_REQUEST_CODE,
            Intent(ACTION_SMS_SENT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val deliveredPendingIntent = PendingIntent.getBroadcast(
            this, SMS_DELIVERED_REQUEST_CODE,
            Intent(ACTION_SMS_DELIVERED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Split long messages if needed
        val parts = smsManager.divideMessage(message)
        
        if (parts.size == 1) {
            smsManager.sendTextMessage(
                phoneNumber,
                null,
                message,
                sentPendingIntent,
                deliveredPendingIntent
            )
        } else {
            val sentIntents = ArrayList<PendingIntent>()
            val deliveredIntents = ArrayList<PendingIntent>()
            
            for (i in parts.indices) {
                sentIntents.add(sentPendingIntent)
                deliveredIntents.add(deliveredPendingIntent)
            }
            
            smsManager.sendMultipartTextMessage(
                phoneNumber,
                null,
                parts,
                sentIntents,
                deliveredIntents
            )
        }
    }
    
    private fun registerSmsReceivers() {
        // SMS sent receiver
        sentReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Log.i(TAG, "Emergency SMS sent successfully")
                    }
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                        Log.e(TAG, "Emergency SMS failed - generic failure")
                    }
                    SmsManager.RESULT_ERROR_NO_SERVICE -> {
                        Log.e(TAG, "Emergency SMS failed - no service")
                    }
                    SmsManager.RESULT_ERROR_NULL_PDU -> {
                        Log.e(TAG, "Emergency SMS failed - null PDU")
                    }
                    SmsManager.RESULT_ERROR_RADIO_OFF -> {
                        Log.e(TAG, "Emergency SMS failed - radio off")
                    }
                }
            }
        }
        
        // SMS delivered receiver
        deliveredReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Log.i(TAG, "Emergency SMS delivered successfully")
                    }
                    Activity.RESULT_CANCELED -> {
                        Log.w(TAG, "Emergency SMS delivery failed")
                    }
                }
            }
        }
        
        // Register receivers with RECEIVER_NOT_EXPORTED for internal use only
        try {
            if (Build.VERSION.SDK_INT >= 33) { // API 33+ requires explicit export flag
                registerReceiver(sentReceiver, IntentFilter(ACTION_SMS_SENT), Context.RECEIVER_NOT_EXPORTED)
                registerReceiver(deliveredReceiver, IntentFilter(ACTION_SMS_DELIVERED), Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(sentReceiver, IntentFilter(ACTION_SMS_SENT))
                registerReceiver(deliveredReceiver, IntentFilter(ACTION_SMS_DELIVERED))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register SMS receivers: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Unregister receivers
        try {
            sentReceiver?.let { unregisterReceiver(it) }
            deliveredReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering SMS receivers", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    /**
     * Create silent emergency notification for foreground service
     */
    private fun createEmergencyNotification(): Notification {
        return NotificationCompat.Builder(this, com.wype.security.WypeApp.WYPE_EMERGENCY_CHANNEL_ID)
            .setContentTitle("") // Empty for stealth
            .setContentText("") // Empty for stealth
            .setSmallIcon(android.R.drawable.ic_dialog_email) // SMS icon
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setColor(Color.TRANSPARENT)
            .setAutoCancel(false)
            .setSilent(true)
            .setVibrate(null)
            .setSound(null)
            .setDefaults(0)
            .build()
    }
}
