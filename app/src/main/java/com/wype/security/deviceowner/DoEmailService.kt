package com.wype.security.deviceowner

interface DoEmailService {
    suspend fun sendProvisioningEmail(
        userEmail: String,
        subject: String = DEFAULT_SUBJECT,
        qrImageBytes: ByteArray? = null
    )

    companion object {
        const val DEFAULT_SUBJECT = "Your Wype Setup – Scan this QR to enable Device Owner"
    }
}
