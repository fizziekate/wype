package com.wype.security.ui.buddy

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.wype.security.utils.PreferencesManager

class BuddyViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BuddyViewModel"
    }

    private val preferencesManager = PreferencesManager(application)
    
    private val _currentBuddy = MutableLiveData<BuddyContact?>().apply {
        value = getCurrentBuddyFromPrefs()
    }
    val currentBuddy: LiveData<BuddyContact?> = _currentBuddy
    
    private val _hasBuddy = MutableLiveData<Boolean>().apply {
        value = preferencesManager.hasBuddyContact()
    }
    val hasBuddy: LiveData<Boolean> = _hasBuddy
    
    private val _lastSmsStatus = MutableLiveData<SmsStatus>()
    val lastSmsStatus: LiveData<SmsStatus> = _lastSmsStatus
    
    data class BuddyContact(
        val name: String,
        val phone: String,
        val dateAdded: Long = System.currentTimeMillis()
    )
    
    enum class SmsStatus {
        PENDING, SENT, DELIVERED, FAILED
    }
    
    fun setBuddyContact(name: String, phone: String) {
        Log.d(TAG, "setBuddyContact called with name='$name', phone='$phone'")
        preferencesManager.setBuddyContact(name, phone)
        _currentBuddy.value = BuddyContact(name, phone)
        _hasBuddy.value = true
        Log.d(TAG, "Buddy contact set - currentBuddy: ${_currentBuddy.value}, hasBuddy: ${_hasBuddy.value}")
    }
    
    fun removeBuddyContact() {
        preferencesManager.setBuddyContact("", "")
        _currentBuddy.value = null
        _hasBuddy.value = false
    }
    
    fun refreshBuddyInfo() {
        Log.d(TAG, "refreshBuddyInfo called")
        val buddyFromPrefs = getCurrentBuddyFromPrefs()
        val hasBuddyFromPrefs = preferencesManager.hasBuddyContact()
        
        Log.d(TAG, "From PreferencesManager - buddy: $buddyFromPrefs, hasBuddy: $hasBuddyFromPrefs")
        
        _currentBuddy.value = buddyFromPrefs
        _hasBuddy.value = hasBuddyFromPrefs
        
        Log.d(TAG, "After refresh - currentBuddy: ${_currentBuddy.value}, hasBuddy: ${_hasBuddy.value}")
    }
    
    fun updateSmsStatus(status: SmsStatus) {
        _lastSmsStatus.value = status
    }
    
    private fun getCurrentBuddyFromPrefs(): BuddyContact? {
        val name = preferencesManager.getBuddyName()
        val phone = preferencesManager.getBuddyPhone()
        
        Log.d(TAG, "getCurrentBuddyFromPrefs - name: '$name', phone: '$phone'")
        
        return if (!name.isNullOrEmpty() && !phone.isNullOrEmpty()) {
            val buddy = BuddyContact(name, phone)
            Log.d(TAG, "Created buddy contact: $buddy")
            buddy
        } else {
            Log.d(TAG, "No valid buddy contact found in preferences")
            null
        }
    }
    
    fun getBuddyContactForEmergency(): BuddyContact? {
        return _currentBuddy.value
    }
}
