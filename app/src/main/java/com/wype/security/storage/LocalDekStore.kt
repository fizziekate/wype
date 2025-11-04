package com.wype.security.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wype.security.crypto.CryptoManager

class LocalDekStore(private val ctx: Context) {
    private val masterKey by lazy {
        MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            ctx,
            "dek_store",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveDek(dek: ByteArray) {
        prefs.edit().putString(KEY_DEK, CryptoManager.b64(dek)).apply()
    }

    fun loadDek(): ByteArray? {
        val b64 = prefs.getString(KEY_DEK, null) ?: return null
        return CryptoManager.b64d(b64)
    }

    fun saveWrapped(kid: String, wrapped: String) {
        prefs.edit()
            .putString(KEY_KID, kid)
            .putString(KEY_WRAPPED, wrapped)
            .apply()
    }

    fun loadWrapped(): Pair<String, String>? {
        val kid = prefs.getString(KEY_KID, null)
        val wrapped = prefs.getString(KEY_WRAPPED, null)
        return if (kid != null && wrapped != null) kid to wrapped else null
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_DEK = "dek"
        private const val KEY_KID = "kid"
        private const val KEY_WRAPPED = "wrappedDek"
    }
}
