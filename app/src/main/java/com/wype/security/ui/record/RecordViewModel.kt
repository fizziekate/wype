package com.wype.security.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.wype.security.utils.PreferencesManager
import java.io.File

class RecordViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    
    private val _currentPhrase = MutableLiveData<String?>().apply {
        value = preferencesManager.getWakePhrase()
    }
    val currentPhrase: LiveData<String?> = _currentPhrase
    
    private val _hasAudioFile = MutableLiveData<Boolean>().apply {
        value = hasValidAudioFile()
    }
    val hasAudioFile: LiveData<Boolean> = _hasAudioFile
    
    private val _recordingQuality = MutableLiveData<RecordingQuality>()
    val recordingQuality: LiveData<RecordingQuality> = _recordingQuality
    
    enum class RecordingQuality {
        POOR, FAIR, GOOD, EXCELLENT
    }
    
    fun saveWakePhrase(phrase: String) {
        preferencesManager.setWakePhrase(phrase)
        _currentPhrase.value = phrase
    }
    
    /**
     * Save both wake phrase text and audio file atomically
     */
    fun saveWakePhraseWithAudio(phrase: String, audioPath: String) {
        // Save both in a single transaction
        preferencesManager.setWakePhrase(phrase)
        preferencesManager.setWakePhraseAudioPath(audioPath)
        
        // Update UI state
        _currentPhrase.value = phrase
        _hasAudioFile.value = true
        
        // Analyze recording quality
        analyzeRecordingQuality(audioPath)
    }
    
    fun deleteWakePhrase() {
        preferencesManager.setWakePhrase("")
        preferencesManager.setWakePhraseAudioPath("")
        _currentPhrase.value = null
        
        // Delete audio file if it exists
        val audioPath = preferencesManager.getWakePhraseAudioPath()
        if (!audioPath.isNullOrEmpty()) {
            try {
                File(audioPath).delete()
            } catch (e: Exception) {
                // Ignore file deletion errors
            }
        }
        
        _hasAudioFile.value = false
    }
    
    fun setAudioFile(filePath: String) {
        preferencesManager.setWakePhraseAudioPath(filePath)
        _hasAudioFile.value = true
        
        // Analyze recording quality
        analyzeRecordingQuality(filePath)
    }
    
    fun getAudioFilePath(): String? {
        return preferencesManager.getWakePhraseAudioPath()
    }
    
    private fun hasValidAudioFile(): Boolean {
        val audioPath = preferencesManager.getWakePhraseAudioPath()
        if (audioPath.isNullOrEmpty()) return false
        
        val file = File(audioPath)
        return file.exists() && file.length() > 0
    }
    
    private fun analyzeRecordingQuality(filePath: String) {
        try {
            val file = File(filePath)
            val fileSize = file.length()
            
            // Simple quality assessment based on file size
            // This is a basic implementation - could be enhanced with actual audio analysis
            val quality = when {
                fileSize < 1000 -> RecordingQuality.POOR
                fileSize < 5000 -> RecordingQuality.FAIR
                fileSize < 20000 -> RecordingQuality.GOOD
                else -> RecordingQuality.EXCELLENT
            }
            
            _recordingQuality.value = quality
            
        } catch (e: Exception) {
            _recordingQuality.value = RecordingQuality.POOR
        }
    }
    
    fun getRecordingInfo(): RecordingInfo? {
        val audioPath = getAudioFilePath()
        if (audioPath.isNullOrEmpty()) return null
        
        try {
            val file = File(audioPath)
            if (!file.exists()) return null
            
            return RecordingInfo(
                filePath = audioPath,
                fileSize = file.length(),
                lastModified = file.lastModified(),
                quality = _recordingQuality.value ?: RecordingQuality.POOR
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    data class RecordingInfo(
        val filePath: String,
        val fileSize: Long,
        val lastModified: Long,
        val quality: RecordingQuality
    )
}
