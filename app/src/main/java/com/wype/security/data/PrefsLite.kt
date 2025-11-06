package com.wype.security.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object PrefsLite {
    private const val PREFS_NAME = "wype_secure_prefs"
    private const val KEY_REGISTRATION_EMAIL = "registration_email"
    
    private var prefs: SharedPreferences? = null
    
    fun init(context: Context) {
        if (prefs == null) {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
    
    fun saveRegistrationEmail(email: String) {
        prefs?.edit()?.putString(KEY_REGISTRATION_EMAIL, email)?.apply()
    }
    
    fun getRegistrationEmail(): String? {
        return prefs?.getString(KEY_REGISTRATION_EMAIL, null)
    }
}
