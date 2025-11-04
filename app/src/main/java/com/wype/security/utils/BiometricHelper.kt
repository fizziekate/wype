package com.wype.security.utils

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.wype.security.R

/**
 * Helper class for biometric authentication (fingerprint, face unlock, etc.)
 */
class BiometricHelper(private val activity: FragmentActivity) {

    companion object {
        private const val TAG = "BiometricHelper"
    }

    /**
     * Check if biometric authentication is available on the device
     */
    fun isBiometricAvailable(): BiometricAvailability {
        val biometricManager = BiometricManager.from(activity)
        
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Log.d(TAG, "Biometric authentication is available")
                BiometricAvailability.AVAILABLE
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Log.w(TAG, "No biometric hardware available")
                BiometricAvailability.NO_HARDWARE
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Log.w(TAG, "Biometric hardware is currently unavailable")
                BiometricAvailability.HARDWARE_UNAVAILABLE
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.w(TAG, "No biometric credentials enrolled")
                BiometricAvailability.NONE_ENROLLED
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                Log.w(TAG, "Security update required for biometric authentication")
                BiometricAvailability.SECURITY_UPDATE_REQUIRED
            }
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                Log.w(TAG, "Biometric authentication is not supported")
                BiometricAvailability.UNSUPPORTED
            }
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                Log.w(TAG, "Biometric status unknown")
                BiometricAvailability.UNKNOWN
            }
            else -> {
                Log.w(TAG, "Biometric authentication unavailable")
                BiometricAvailability.UNAVAILABLE
            }
        }
    }

    /**
     * Authenticate user using biometrics
     */
    fun authenticate(
        title: String = "Biometric Authentication",
        subtitle: String = "Use your fingerprint to login to WYPE Security",
        negativeButtonText: String = "Use Password",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED -> {
                            Log.d(TAG, "Biometric authentication cancelled by user")
                            onCancel()
                        }
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            Log.d(TAG, "User chose to use alternative authentication")
                            onCancel()
                        }
                        else -> {
                            Log.e(TAG, "Biometric authentication error: $errString")
                            onError("Authentication error: $errString")
                        }
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.i(TAG, "Biometric authentication succeeded")
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "Biometric authentication failed - biometric not recognized")
                    onError("Fingerprint not recognized. Please try again.")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting biometric authentication", e)
            onError("Failed to start biometric authentication: ${e.message}")
        }
    }

    /**
     * Get user-friendly message about biometric availability
     */
    fun getBiometricStatusMessage(): String {
        return when (isBiometricAvailable()) {
            BiometricAvailability.AVAILABLE -> "Fingerprint login is available"
            BiometricAvailability.NO_HARDWARE -> "This device doesn't support fingerprint authentication"
            BiometricAvailability.HARDWARE_UNAVAILABLE -> "Fingerprint sensor is currently unavailable"
            BiometricAvailability.NONE_ENROLLED -> "No fingerprints enrolled. Please add a fingerprint in Settings"
            BiometricAvailability.SECURITY_UPDATE_REQUIRED -> "Security update required for fingerprint authentication"
            BiometricAvailability.UNSUPPORTED -> "Fingerprint authentication is not supported on this device"
            BiometricAvailability.UNKNOWN -> "Fingerprint status unknown"
            BiometricAvailability.UNAVAILABLE -> "Fingerprint authentication is not available"
        }
    }

    /**
     * Check if device supports any type of biometric authentication
     */
    fun hasBiometricCapability(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or 
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Show authentication with fallback to device credentials (PIN, pattern, password)
     */
    fun authenticateWithFallback(
        title: String = "Secure Login",
        subtitle: String = "Use biometrics or device PIN to login",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.e(TAG, "Authentication error: $errString")
                    onError("Authentication failed: $errString")
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.i(TAG, "Authentication succeeded")
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "Authentication failed")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or 
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting authentication", e)
            onError("Failed to start authentication: ${e.message}")
        }
    }

    enum class BiometricAvailability {
        AVAILABLE,
        NO_HARDWARE,
        HARDWARE_UNAVAILABLE,
        NONE_ENROLLED,
        SECURITY_UPDATE_REQUIRED,
        UNSUPPORTED,
        UNKNOWN,
        UNAVAILABLE
    }
}
