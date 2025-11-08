package com.wype.security.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object PrefsLite {
    private const val PREFS_NAME = "wype_secure_prefs"
    private const val KEY_REGISTRATION_EMAIL = "registration_email"
    
    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    fun putRegistrationEmail(context: Context, email: String) {
        getEncryptedPrefs(context).edit().putString(KEY_REGISTRATION_EMAIL, email).apply()
    }
    
    fun getRegistrationEmail(context: Context): String? {
        return getEncryptedPrefs(context).getString(KEY_REGISTRATION_EMAIL, null)
    }
}
