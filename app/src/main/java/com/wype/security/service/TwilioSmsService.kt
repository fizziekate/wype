package com.wype.security.service

import android.content.Context
import android.util.Log
import com.twilio.Twilio
import com.twilio.rest.api.v2010.account.Message
import com.twilio.type.PhoneNumber
import com.wype.security.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Service class to handle Twilio SMS sending for emergency alerts
 * Operates silently in background without user interaction
 */
class TwilioSmsService(private val context: Context) {

    companion object {
        private const val TAG = "TwilioSmsService"
    }

    private val preferencesManager = PreferencesManager(context)

    /**
     * Send SMS via Twilio API silently
     * @param toPhoneNumber Recipient phone number
     * @param messageBody SMS message content
     * @param onSuccess Callback for successful send
     * @param onError Callback for error handling
     */
    fun sendSms(
        toPhoneNumber: String,
        messageBody: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        // Launch in IO thread for network operation
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = sendSmsSync(toPhoneNumber, messageBody)
                
                if (result) {
                    withContext(Dispatchers.Main) {
                        onSuccess?.invoke()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError?.invoke(Exception("Twilio SMS send failed - unknown error"))
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending Twilio SMS", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke(e)
                }
            }
        }
    }

    /**
     * Synchronous SMS sending via Twilio
     * @param toPhoneNumber Recipient phone number
     * @param messageBody SMS message content
     * @return true if SMS was sent successfully, false otherwise
     */
    private fun sendSmsSync(toPhoneNumber: String, messageBody: String): Boolean {
        try {
            // Get Twilio credentials from preferences
            val accountSid = preferencesManager.getTwilioAccountSid()
            val authToken = preferencesManager.getTwilioAuthToken()
            val fromPhoneNumber = preferencesManager.getTwilioFromPhone()

            // Check if Twilio is configured
            if (accountSid.isNullOrEmpty() || authToken.isNullOrEmpty() || fromPhoneNumber.isNullOrEmpty()) {
                Log.w(TAG, "Twilio not configured - missing credentials")
                return false
            }

            // Initialize Twilio
            Twilio.init(accountSid, authToken)

            // Format phone numbers
            val fromPhone = PhoneNumber(fromPhoneNumber)
            val toPhone = PhoneNumber(toPhoneNumber)

            // Send SMS
            val message = Message.creator(toPhone, fromPhone, messageBody).create()

            Log.i(TAG, "Twilio SMS sent successfully. SID: ${message.sid}")
            Log.i(TAG, "SMS Status: ${message.status}")
            
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Twilio SMS", e)
            return false
        }
    }

    /**
     * Test Twilio configuration
     * @return true if Twilio is properly configured, false otherwise
     */
    fun isTwilioConfigured(): Boolean {
        val accountSid = preferencesManager.getTwilioAccountSid()
        val authToken = preferencesManager.getTwilioAuthToken()
        val fromPhoneNumber = preferencesManager.getTwilioFromPhone()

        return !accountSid.isNullOrEmpty() && 
               !authToken.isNullOrEmpty() && 
               !fromPhoneNumber.isNullOrEmpty()
    }

    /**
     * Validate phone number format for Twilio
     * @param phoneNumber Phone number to validate
     * @return true if valid format (starts with +), false otherwise
     */
    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        return phoneNumber.startsWith("+") && phoneNumber.length >= 10
    }

    /**
     * Format phone number for Twilio (ensure it starts with +)
     * @param phoneNumber Raw phone number
     * @param countryCode Country code to prepend if needed
     * @return Formatted phone number with + prefix
     */
    fun formatPhoneNumber(phoneNumber: String, countryCode: String = "+1"): String {
        return if (phoneNumber.startsWith("+")) {
            phoneNumber
        } else {
            // Remove any non-digit characters
            val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
            "$countryCode$cleanNumber"
        }
    }
}
