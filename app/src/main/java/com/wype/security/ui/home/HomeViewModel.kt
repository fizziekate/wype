package com.wype.security.ui.home

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.wype.security.service.SpeechListenerService
import com.wype.security.services.PorcupineService
import com.wype.security.utils.PreferencesManager

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    
    private val _serviceStatus = MutableLiveData<Boolean>().apply {
        value = preferencesManager.isServiceEnabled()
    }
    val serviceStatus: LiveData<Boolean> = _serviceStatus

    private val _isListening = MutableLiveData<Boolean>().apply {
        value = preferencesManager.isServiceEnabled()
    }
    val isListening: LiveData<Boolean> = _isListening
    
    private val _setupStatus = MutableLiveData<SetupStatus>().apply {
        value = checkSetupStatus()
    }
    val setupStatus: LiveData<SetupStatus> = _setupStatus
    
    data class SetupStatus(
        val hasWakePhrase: Boolean,
        val hasBuddy: Boolean,
        val hasDeviceAdmin: Boolean,
        val isFullySetup: Boolean
    )
    
    fun toggleService() {
        val currentStatus = _serviceStatus.value ?: false
        val newStatus = !currentStatus
        
        if (newStatus) {
            startSpeechService()
        } else {
            stopSpeechService()
        }
        
        _serviceStatus.value = newStatus
        _isListening.value = newStatus
        preferencesManager.setServiceEnabled(newStatus)
    }
    
    private fun startSpeechService() {
        // Check if Porcupine is enabled and configured
        if (preferencesManager.isPorcupineEnabled() && preferencesManager.isPorcupineConfigured()) {
            // Use Porcupine service
            val intent = Intent(getApplication(), PorcupineService::class.java).apply {
                action = PorcupineService.ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(intent)
            } else {
                getApplication<Application>().startService(intent)
            }
        } else {
            // Fall back to traditional speech listener service
            val intent = Intent(getApplication(), SpeechListenerService::class.java).apply {
                action = SpeechListenerService.ACTION_START_LISTENING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(intent)
            } else {
                getApplication<Application>().startService(intent)
            }
        }
    }
    
    private fun stopSpeechService() {
        // Stop both services to ensure clean shutdown
        
        // Stop Porcupine service
        val porcupineIntent = Intent(getApplication(), PorcupineService::class.java).apply {
            action = PorcupineService.ACTION_STOP_SERVICE
        }
        getApplication<Application>().startService(porcupineIntent)
        
        // Stop traditional speech service
        val speechIntent = Intent(getApplication(), SpeechListenerService::class.java).apply {
            action = SpeechListenerService.ACTION_STOP_LISTENING
        }
        getApplication<Application>().startService(speechIntent)
    }
    
    fun refreshSetupStatus() {
        _setupStatus.value = checkSetupStatus()
    }
    
    private fun checkSetupStatus(): SetupStatus {
        val hasTraditionalWakePhrase = preferencesManager.hasWakePhrase()
        val hasPorcupineConfigured = preferencesManager.isPorcupineEnabled() && preferencesManager.isPorcupineConfigured()
        
        // User has wake detection if they have either traditional wake phrase OR Porcupine configured
        val hasWakePhrase = hasTraditionalWakePhrase || hasPorcupineConfigured
        
        val hasBuddy = preferencesManager.hasBuddyContact()
        val hasDeviceAdmin = preferencesManager.isDeviceAdminEnabled()
        
        return SetupStatus(
            hasWakePhrase = hasWakePhrase,
            hasBuddy = hasBuddy,
            hasDeviceAdmin = hasDeviceAdmin,
            isFullySetup = hasWakePhrase && hasBuddy && hasDeviceAdmin
        )
    }
    
    fun updateServiceStatus(isRunning: Boolean) {
        _serviceStatus.value = isRunning
    }
    
    fun updateListeningStatus(listening: Boolean) {
        _isListening.value = listening
    }
}
