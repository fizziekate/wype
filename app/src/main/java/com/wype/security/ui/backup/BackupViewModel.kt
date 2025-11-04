package com.wype.security.ui.backup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.wype.security.utils.PreferencesManager

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    
    private val _isBackupEnabled = MutableLiveData<Boolean>().apply {
        value = preferencesManager.isBackupEnabled()
    }
    val isBackupEnabled: LiveData<Boolean> = _isBackupEnabled
    
    private val _googleAccount = MutableLiveData<GoogleSignInAccount?>()
    val googleAccount: LiveData<GoogleSignInAccount?> = _googleAccount
    
    private val _lastBackupTime = MutableLiveData<Long?>().apply {
        value = getLastBackupFromPrefs()
    }
    val lastBackupTime: LiveData<Long?> = _lastBackupTime
    
    private val _backupStatus = MutableLiveData<String>()
    val backupStatus: LiveData<String> = _backupStatus
    
    private val _backupProgress = MutableLiveData<Int>()
    val backupProgress: LiveData<Int> = _backupProgress
    
    fun setBackupEnabled(enabled: Boolean) {
        preferencesManager.setBackupEnabled(enabled)
        _isBackupEnabled.value = enabled
        
        if (enabled) {
            val account = _googleAccount.value
            if (account != null) {
                preferencesManager.setGoogleAccount(account.email ?: "")
            }
        }
    }
    
    fun setGoogleAccount(account: GoogleSignInAccount?) {
        _googleAccount.value = account
        if (account != null) {
            preferencesManager.setGoogleAccount(account.email ?: "")
        } else {
            preferencesManager.setGoogleAccount("")
            // Disable backup if account is removed
            setBackupEnabled(false)
        }
    }
    
    fun setLastBackupTime(timestamp: Long) {
        preferencesManager.setLastBackupTime(timestamp)
        _lastBackupTime.value = timestamp
    }
    
    fun setBackupStatus(status: String) {
        _backupStatus.value = status
    }
    
    fun setBackupProgress(progress: Int) {
        _backupProgress.value = progress
    }
    
    fun refreshBackupStatus() {
        _isBackupEnabled.value = preferencesManager.isBackupEnabled()
        _lastBackupTime.value = getLastBackupFromPrefs()
    }
    
    private fun getLastBackupFromPrefs(): Long? {
        val timestamp = preferencesManager.getLastBackupTime()
        return if (timestamp > 0) timestamp else null
    }
    
    fun getBackupSummary(): BackupSummary {
        val account = _googleAccount.value
        val enabled = _isBackupEnabled.value ?: false
        val lastBackup = _lastBackupTime.value
        
        return BackupSummary(
            isEnabled = enabled,
            hasGoogleAccount = account != null,
            googleEmail = account?.email,
            lastBackupTimestamp = lastBackup,
            isFullyConfigured = enabled && account != null
        )
    }
    
    data class BackupSummary(
        val isEnabled: Boolean,
        val hasGoogleAccount: Boolean,
        val googleEmail: String?,
        val lastBackupTimestamp: Long?,
        val isFullyConfigured: Boolean
    )
}
