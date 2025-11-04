package com.wype.security.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val AES = "AES"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private val rng = SecureRandom()

    fun newDek(): ByteArray {
        val b = ByteArray(32)
        rng.nextBytes(b)
        return b
    }

    private fun key(dek: ByteArray): SecretKey = SecretKeySpec(dek, AES)

    data class Box(val iv: ByteArray, val ct: ByteArray)

    fun encrypt(dek: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): Box {
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.ENCRYPT_MODE, key(dek), GCMParameterSpec(GCM_TAG_BITS, iv))
            if (aad != null) updateAAD(aad)
        }
        val ct = cipher.doFinal(plaintext)
        return Box(iv, ct)
    }

    fun decrypt(dek: ByteArray, iv: ByteArray, ct: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, key(dek), GCMParameterSpec(GCM_TAG_BITS, iv))
            if (aad != null) updateAAD(aad)
        }
        return cipher.doFinal(ct)
    }

    fun b64(data: ByteArray) = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
    fun b64d(s: String) = android.util.Base64.decode(s, android.util.Base64.NO_WRAP)
}
