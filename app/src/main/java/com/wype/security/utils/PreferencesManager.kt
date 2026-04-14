package com.wype.security.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Centralized preferences management for WYPE app
 */
class PreferencesManager(context: Context) {
    
    companion object {
        private const val PREF_NAME = "wype_preferences"
        private const val KEY_WAKE_PHRASE = "wake_phrase"
        private const val KEY_WAKE_PHRASE_AUDIO = "wake_phrase_audio"
        private const val KEY_BUDDY_NAME = "buddy_name"
        private const val KEY_BUDDY_PHONE = "buddy_phone"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_DEVICE_ADMIN_ENABLED = "device_admin_enabled"
        private const val KEY_BACKUP_ENABLED = "backup_enabled"
        private const val KEY_GOOGLE_ACCOUNT = "google_account"
        private const val KEY_PHRASE_DETECTIONS_LOG = "phrase_detections_log"
        private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_USER_LOGGED_IN = "user_logged_in"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BIOMETRIC_EMAIL = "biometric_email"
        private const val KEY_BIOMETRIC_PASSWORD = "biometric_password"
        
        // Twilio SMS Configuration
        private const val KEY_TWILIO_ACCOUNT_SID = "twilio_account_sid"
        private const val KEY_TWILIO_AUTH_TOKEN = "twilio_auth_token"
        private const val KEY_TWILIO_FROM_PHONE = "twilio_from_phone"
        private const val KEY_TWILIO_ENABLED = "twilio_enabled"
        
        // Azure Speech Configuration
        private const val KEY_AZURE_SPEECH_KEY = "azure_speech_key"
        private const val KEY_AZURE_SPEECH_REGION = "azure_speech_region"
        private const val KEY_AZURE_SPEECH_ENABLED = "azure_speech_enabled"
        private const val KEY_SPEECH_RECOGNITION_MODE = "speech_recognition_mode"
        
        // Porcupine Configuration
        private const val KEY_PORCUPINE_ACCESS_KEY = "porcupine_access_key"
        private const val KEY_PORCUPINE_WAKE_WORD = "porcupine_wake_word"
        private const val KEY_PORCUPINE_SENSITIVITY = "porcupine_sensitivity"
        private const val KEY_PORCUPINE_ENABLED = "porcupine_enabled"
        
        // Lightweight ML Configuration
        private const val KEY_ML_DETECTION_MODE = "ml_detection_mode"
        private const val KEY_ML_PERFORMANCE_PROFILE = "ml_performance_profile"
        private const val KEY_ML_CUSTOM_MODEL_PATH = "ml_custom_model_path"
        private const val KEY_ML_DETECTION_THRESHOLD = "ml_detection_threshold"
        private const val KEY_ML_ENABLED = "ml_enabled"
        
        // Emergency SMS Tracking (prevent spam/duplicates)
        private const val KEY_LAST_EMERGENCY_SMS_TIME = "last_emergency_sms_time"
        
        // Protection Mode Configuration
        private const val KEY_PROTECTION_MODE_ENABLED = "protection_mode_enabled"
        private const val KEY_PROTECTION_MODE_START_TIME = "protection_mode_start_time"
        private const val KEY_PROTECTION_MODE_EMERGENCY_COUNT = "protection_mode_emergency_count"
        private const val KEY_PROTECTION_MODE_LAST_EMERGENCY_DATE = "protection_mode_last_emergency_date"
        
        // Double-Confirmation State Persistence (for crash resilience)
        private const val KEY_CONFIRMATION_COUNT = "confirmation_count"
        private const val KEY_FIRST_CONFIRMATION_TIME = "first_confirmation_time"
        private const val KEY_LAST_CONFIRMATION_TIME = "last_confirmation_time"
        private const val KEY_CONFIRMATION_KEYWORD = "confirmation_keyword"
        private const val KEY_CONFIRMATION_DETECTOR_TYPE = "confirmation_detector_type"
        
        // Audio Settings
        private const val KEY_AUDIO_FEEDBACK_ENABLED = "audio_feedback_enabled"
        private const val KEY_SYSTEM_SOUNDS_MUTED = "system_sounds_muted"
    }
    
    private val sharedPrefs: SharedPreferences = 
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    // Wake Phrase Management
    fun setWakePhrase(phrase: String) {
        sharedPrefs.edit().putString(KEY_WAKE_PHRASE, phrase).apply()
        // Auto-enable service when wake phrase is configured
        if (!phrase.isNullOrEmpty()) {
            setServiceEnabled(true)
        }
    }
    
    fun getWakePhrase(): String? {
        return sharedPrefs.getString(KEY_WAKE_PHRASE, null)
    }
    
    fun hasWakePhrase(): Boolean {
        return !getWakePhrase().isNullOrEmpty()
    }
    
    // Audio file path for recorded wake phrase
    fun setWakePhraseAudioPath(path: String) {
        sharedPrefs.edit().putString(KEY_WAKE_PHRASE_AUDIO, path).apply()
    }
    
    fun getWakePhraseAudioPath(): String? {
        return sharedPrefs.getString(KEY_WAKE_PHRASE_AUDIO, null)
    }
    
    // Buddy Contact Management
    fun setBuddyContact(name: String, phone: String) {
        sharedPrefs.edit()
            .putString(KEY_BUDDY_NAME, name)
            .putString(KEY_BUDDY_PHONE, phone)
            .apply()
    }
    
    fun getBuddyName(): String? {
        return sharedPrefs.getString(KEY_BUDDY_NAME, null)
    }
    
    fun getBuddyPhone(): String? {
        return sharedPrefs.getString(KEY_BUDDY_PHONE, null)
    }
    
    fun hasBuddyContact(): Boolean {
        return !getBuddyPhone().isNullOrEmpty()
    }
    
    // Service Status
    fun setServiceEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }
    
    fun isServiceEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_SERVICE_ENABLED, false)
    }
    
    // Google Backup Settings
    fun setBackupEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_BACKUP_ENABLED, enabled).apply()
    }
    
    fun isBackupEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_BACKUP_ENABLED, false)
    }
    
    fun setGoogleAccount(account: String) {
        sharedPrefs.edit().putString(KEY_GOOGLE_ACCOUNT, account).apply()
    }
    
    fun getGoogleAccount(): String? {
        return sharedPrefs.getString(KEY_GOOGLE_ACCOUNT, null)
    }
    
    // Device Admin Status
    fun setDeviceAdminEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_DEVICE_ADMIN_ENABLED, enabled).apply()
    }
    
    fun isDeviceAdminEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_DEVICE_ADMIN_ENABLED, false)
    }
    
    // Detection Logging (for debugging)
    fun logPhraseDetection(phrase: String, timestamp: Long) {
        val existingLog = sharedPrefs.getString(KEY_PHRASE_DETECTIONS_LOG, "")
        val newEntry = "$timestamp: $phrase\n"
        val updatedLog = existingLog + newEntry
        
        // Keep only last 50 entries to avoid bloating
        val lines = updatedLog.lines()
        val trimmedLog = if (lines.size > 50) {
            lines.takeLast(50).joinToString("\n")
        } else {
            updatedLog
        }
        
        sharedPrefs.edit().putString(KEY_PHRASE_DETECTIONS_LOG, trimmedLog).apply()
    }
    
    fun getDetectionLog(): String {
        return sharedPrefs.getString(KEY_PHRASE_DETECTIONS_LOG, "") ?: ""
    }
    
    fun clearDetectionLog() {
        sharedPrefs.edit().putString(KEY_PHRASE_DETECTIONS_LOG, "").apply()
    }
    
    // Backup Time Tracking
    fun setLastBackupTime(timestamp: Long) {
        sharedPrefs.edit().putLong(KEY_LAST_BACKUP_TIME, timestamp).apply()
    }
    
    fun getLastBackupTime(): Long {
        return sharedPrefs.getLong(KEY_LAST_BACKUP_TIME, 0)
    }
    
    
    // Utility methods
    fun isAppFullyConfigured(): Boolean {
        return hasWakePhrase() && hasBuddyContact() && isDeviceAdminEnabled()
    }
    
    fun clearAllPreferences() {
        sharedPrefs.edit().clear().apply()
    }
    
    // User Authentication Management
    fun registerUser(username: String, password: String) {
        sharedPrefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }
    
    fun validateUser(username: String, password: String): Boolean {
        val storedUsername = sharedPrefs.getString(KEY_USERNAME, null)
        val storedPassword = sharedPrefs.getString(KEY_PASSWORD, null)
        
        return username == storedUsername && password == storedPassword
    }
    
    fun userExists(username: String): Boolean {
        val storedUsername = sharedPrefs.getString(KEY_USERNAME, null)
        return username == storedUsername
    }
    
    fun setUserLoggedIn(loggedIn: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_USER_LOGGED_IN, loggedIn).apply()
    }

    fun isUserLoggedIn(): Boolean {
        return sharedPrefs.getBoolean(KEY_USER_LOGGED_IN, false)
    }

    fun setUserPhone(phone: String) {
        sharedPrefs.edit().putString(KEY_USER_PHONE, phone).apply()
    }

    fun getUserPhone(): String? {
        return sharedPrefs.getString(KEY_USER_PHONE, null)
    }
    
    fun hasRegisteredUser(): Boolean {
        val username = sharedPrefs.getString(KEY_USERNAME, null)
        return !username.isNullOrEmpty()
    }
    
    fun logout() {
        sharedPrefs.edit().putBoolean(KEY_USER_LOGGED_IN, false).apply()
        // Also clear biometric credentials on logout for security
        clearBiometricLoginCredentials()
    }
    
    // Biometric Authentication Management
    fun setBiometricLoginEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }
    
    fun isBiometricLoginEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }
    
    fun setBiometricLoginCredentials(email: String, password: String) {
        // Note: In a production app, you should encrypt the password
        // For demo purposes, we're storing it directly
        sharedPrefs.edit()
            .putString(KEY_BIOMETRIC_EMAIL, email)
            .putString(KEY_BIOMETRIC_PASSWORD, password)
            .putBoolean(KEY_BIOMETRIC_ENABLED, true)
            .apply()
    }
    
    fun getBiometricLoginEmail(): String? {
        return sharedPrefs.getString(KEY_BIOMETRIC_EMAIL, null)
    }
    
    fun getBiometricLoginPassword(): String? {
        return sharedPrefs.getString(KEY_BIOMETRIC_PASSWORD, null)
    }
    
    fun clearBiometricLoginCredentials() {
        sharedPrefs.edit()
            .remove(KEY_BIOMETRIC_ENABLED)
            .remove(KEY_BIOMETRIC_EMAIL)
            .remove(KEY_BIOMETRIC_PASSWORD)
            .apply()
    }
    
    fun hasBiometricLoginSetup(): Boolean {
        return isBiometricLoginEnabled() && 
               !getBiometricLoginEmail().isNullOrEmpty() && 
               !getBiometricLoginPassword().isNullOrEmpty()
    }
    
    // Twilio SMS Configuration Management
    fun setTwilioCredentials(accountSid: String, authToken: String, fromPhone: String) {
        sharedPrefs.edit()
            .putString(KEY_TWILIO_ACCOUNT_SID, accountSid)
            .putString(KEY_TWILIO_AUTH_TOKEN, authToken)
            .putString(KEY_TWILIO_FROM_PHONE, fromPhone)
            .putBoolean(KEY_TWILIO_ENABLED, true)
            .apply()
    }
    
    fun getTwilioAccountSid(): String? {
        return sharedPrefs.getString(KEY_TWILIO_ACCOUNT_SID, null)
    }
    
    fun getTwilioAuthToken(): String? {
        return sharedPrefs.getString(KEY_TWILIO_AUTH_TOKEN, null)
    }
    
    fun getTwilioFromPhone(): String? {
        return sharedPrefs.getString(KEY_TWILIO_FROM_PHONE, null)
    }
    
    fun setTwilioEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_TWILIO_ENABLED, enabled).apply()
    }
    
    fun isTwilioEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_TWILIO_ENABLED, false)
    }
    
    fun isTwilioConfigured(): Boolean {
        return !getTwilioAccountSid().isNullOrEmpty() &&
               !getTwilioAuthToken().isNullOrEmpty() &&
               !getTwilioFromPhone().isNullOrEmpty()
    }
    
    fun clearTwilioCredentials() {
        sharedPrefs.edit()
            .remove(KEY_TWILIO_ACCOUNT_SID)
            .remove(KEY_TWILIO_AUTH_TOKEN)
            .remove(KEY_TWILIO_FROM_PHONE)
            .remove(KEY_TWILIO_ENABLED)
            .apply()
    }
    
    // Azure Speech Configuration Management
    fun setAzureSpeechCredentials(speechKey: String, region: String) {
        sharedPrefs.edit()
            .putString(KEY_AZURE_SPEECH_KEY, speechKey)
            .putString(KEY_AZURE_SPEECH_REGION, region)
            .apply()
    }
    
    fun getAzureSpeechKey(): String? {
        return sharedPrefs.getString(KEY_AZURE_SPEECH_KEY, null)
    }
    
    fun getAzureSpeechRegion(): String? {
        return sharedPrefs.getString(KEY_AZURE_SPEECH_REGION, null)
    }
    
    fun setAzureSpeechEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_AZURE_SPEECH_ENABLED, enabled).apply()
    }
    
    fun isAzureSpeechEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_AZURE_SPEECH_ENABLED, false)
    }
    
    fun isAzureSpeechConfigured(): Boolean {
        return !getAzureSpeechKey().isNullOrEmpty() &&
               !getAzureSpeechRegion().isNullOrEmpty()
    }
    
    fun clearAzureSpeechCredentials() {
        sharedPrefs.edit()
            .remove(KEY_AZURE_SPEECH_KEY)
            .remove(KEY_AZURE_SPEECH_REGION)
            .remove(KEY_AZURE_SPEECH_ENABLED)
            .apply()
    }
    
    // Speech Recognition Mode Management
    enum class SpeechRecognitionMode {
        LOCAL_ANDROID,    // Default Android SpeechRecognizer
        AZURE_COGNITIVE,  // Azure Cognitive Services
        AUTO              // Try Azure first, fallback to local
    }
    
    fun setSpeechRecognitionMode(mode: SpeechRecognitionMode) {
        sharedPrefs.edit().putString(KEY_SPEECH_RECOGNITION_MODE, mode.name).apply()
    }
    
    fun getSpeechRecognitionMode(): SpeechRecognitionMode {
        val modeName = sharedPrefs.getString(KEY_SPEECH_RECOGNITION_MODE, SpeechRecognitionMode.AUTO.name)
        return try {
            SpeechRecognitionMode.valueOf(modeName ?: SpeechRecognitionMode.AUTO.name)
        } catch (e: Exception) {
            SpeechRecognitionMode.AUTO
        }
    }
    
    // Porcupine Configuration Management
    fun setPorcupineAccessKey(accessKey: String) {
        sharedPrefs.edit().putString(KEY_PORCUPINE_ACCESS_KEY, accessKey).apply()
    }
    
    fun getPorcupineAccessKey(): String? {
        return sharedPrefs.getString(KEY_PORCUPINE_ACCESS_KEY, null)
    }
    
    fun setPorcupineWakeWord(wakeWord: String) {
        sharedPrefs.edit().putString(KEY_PORCUPINE_WAKE_WORD, wakeWord).apply()
    }
    
    fun getPorcupineWakeWord(): String? {
        return sharedPrefs.getString(KEY_PORCUPINE_WAKE_WORD, "picovoice") // Default wake word
    }
    
    fun setPorcupineSensitivity(sensitivity: Float) {
        sharedPrefs.edit().putFloat(KEY_PORCUPINE_SENSITIVITY, sensitivity).apply()
    }
    
    fun getPorcupineSensitivity(): Float {
        return sharedPrefs.getFloat(KEY_PORCUPINE_SENSITIVITY, 0.5f) // Default sensitivity
    }
    
    fun setPorcupineEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_PORCUPINE_ENABLED, enabled).apply()
    }
    
    fun isPorcupineEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_PORCUPINE_ENABLED, false)
    }
    
    fun isPorcupineConfigured(): Boolean {
        return !getPorcupineAccessKey().isNullOrEmpty()
    }
    
    fun clearPorcupineCredentials() {
        sharedPrefs.edit()
            .remove(KEY_PORCUPINE_ACCESS_KEY)
            .remove(KEY_PORCUPINE_WAKE_WORD)
            .remove(KEY_PORCUPINE_SENSITIVITY)
            .remove(KEY_PORCUPINE_ENABLED)
            .apply()
    }
    
    // Lightweight ML Configuration Management
    enum class MLDetectionMode {
        PORCUPINE,        // Picovoice Porcupine (existing)
        TENSORFLOW_LITE,  // TensorFlow Lite models
        SIMPLE_NEURAL,    // Custom lightweight neural network
        AUTO              // Automatically choose best option
    }
    
    enum class MLPerformanceProfile {
        ULTRA_LOW_POWER,  // Minimal CPU usage, basic detection
        BALANCED,         // Good balance of accuracy and efficiency
        HIGH_ACCURACY     // Maximum accuracy, higher resource usage
    }
    
    fun setMLDetectionMode(mode: MLDetectionMode) {
        sharedPrefs.edit().putString(KEY_ML_DETECTION_MODE, mode.name).apply()
    }
    
    fun getMLDetectionMode(): MLDetectionMode {
        val modeName = sharedPrefs.getString(KEY_ML_DETECTION_MODE, MLDetectionMode.AUTO.name)
        return try {
            MLDetectionMode.valueOf(modeName ?: MLDetectionMode.AUTO.name)
        } catch (e: Exception) {
            MLDetectionMode.AUTO
        }
    }
    
    fun setMLPerformanceProfile(profile: MLPerformanceProfile) {
        sharedPrefs.edit().putString(KEY_ML_PERFORMANCE_PROFILE, profile.name).apply()
    }
    
    fun getMLPerformanceProfile(): MLPerformanceProfile {
        val profileName = sharedPrefs.getString(KEY_ML_PERFORMANCE_PROFILE, MLPerformanceProfile.BALANCED.name)
        return try {
            MLPerformanceProfile.valueOf(profileName ?: MLPerformanceProfile.BALANCED.name)
        } catch (e: Exception) {
            MLPerformanceProfile.BALANCED
        }
    }
    
    fun setMLCustomModelPath(modelPath: String) {
        sharedPrefs.edit().putString(KEY_ML_CUSTOM_MODEL_PATH, modelPath).apply()
    }
    
    fun getMLCustomModelPath(): String? {
        return sharedPrefs.getString(KEY_ML_CUSTOM_MODEL_PATH, null)
    }
    
    fun setMLDetectionThreshold(threshold: Float) {
        sharedPrefs.edit().putFloat(KEY_ML_DETECTION_THRESHOLD, threshold).apply()
    }
    
    fun getMLDetectionThreshold(): Float {
        return sharedPrefs.getFloat(KEY_ML_DETECTION_THRESHOLD, 0.7f) // Default threshold
    }
    
    fun setMLEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_ML_ENABLED, enabled).apply()
    }
    
    fun isMLEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_ML_ENABLED, true) // Default to enabled
    }
    
    fun isMLConfigured(): Boolean {
        val mode = getMLDetectionMode()
        return when (mode) {
            MLDetectionMode.PORCUPINE -> isPorcupineConfigured()
            MLDetectionMode.TENSORFLOW_LITE -> !getMLCustomModelPath().isNullOrEmpty()
            MLDetectionMode.SIMPLE_NEURAL -> true // Always configured
            MLDetectionMode.AUTO -> true // Auto mode doesn't require specific config
        }
    }
    
    fun clearMLCredentials() {
        sharedPrefs.edit()
            .remove(KEY_ML_DETECTION_MODE)
            .remove(KEY_ML_PERFORMANCE_PROFILE)
            .remove(KEY_ML_CUSTOM_MODEL_PATH)
            .remove(KEY_ML_DETECTION_THRESHOLD)
            .remove(KEY_ML_ENABLED)
            .apply()
    }
    
    // Unified wake word detection configuration check
    fun isWakeWordDetectionConfigured(): Boolean {
        return isPorcupineConfigured() || isMLConfigured()
    }
    
    // Get the preferred wake word detection method
    fun getPreferredWakeWordMethod(): String {
        return when {
            isMLEnabled() && isMLConfigured() -> "Lightweight ML (${getMLDetectionMode().name})"
            isPorcupineEnabled() && isPorcupineConfigured() -> "Porcupine"
            else -> "Not Configured"
        }
    }
    
    // Emergency SMS Spam Prevention
    fun setLastEmergencySmsTime(timestamp: Long) {
        sharedPrefs.edit().putLong(KEY_LAST_EMERGENCY_SMS_TIME, timestamp).apply()
    }
    
    fun getLastEmergencySmsTime(): Long {
        return sharedPrefs.getLong(KEY_LAST_EMERGENCY_SMS_TIME, 0L)
    }
    
    fun getMinutesSinceLastEmergencySms(): Long {
        val lastTime = getLastEmergencySmsTime()
        return if (lastTime > 0) {
            (System.currentTimeMillis() - lastTime) / 60000L // Convert to minutes
        } else {
            Long.MAX_VALUE // No previous SMS
        }
    }
    
    fun canSendEmergencySms(minimumMinutes: Int = 5): Boolean {
        return getMinutesSinceLastEmergencySms() >= minimumMinutes
    }
    
    // Protection Mode Management
    fun setProtectionModeEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_PROTECTION_MODE_ENABLED, enabled).apply()
        if (!enabled) {
            // Clear protection mode data when disabled
            clearProtectionModeData()
        }
    }
    
    fun isProtectionModeEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_PROTECTION_MODE_ENABLED, false)
    }
    
    fun setProtectionModeStartTime(timestamp: Long) {
        sharedPrefs.edit().putLong(KEY_PROTECTION_MODE_START_TIME, timestamp).apply()
    }
    
    fun getProtectionModeStartTime(): Long {
        return sharedPrefs.getLong(KEY_PROTECTION_MODE_START_TIME, 0L)
    }
    
    fun getProtectionModeDurationHours(): Long {
        val startTime = getProtectionModeStartTime()
        return if (startTime > 0) {
            (System.currentTimeMillis() - startTime) / (1000 * 60 * 60) // Convert to hours
        } else {
            0L
        }
    }
    
    fun setProtectionModeEmergencyCount(count: Int) {
        sharedPrefs.edit().putInt(KEY_PROTECTION_MODE_EMERGENCY_COUNT, count).apply()
    }
    
    fun getProtectionModeEmergencyCount(): Int {
        return sharedPrefs.getInt(KEY_PROTECTION_MODE_EMERGENCY_COUNT, 0)
    }
    
    fun incrementProtectionModeEmergencyCount(): Int {
        val newCount = getProtectionModeEmergencyCount() + 1
        setProtectionModeEmergencyCount(newCount)
        return newCount
    }
    
    fun resetProtectionModeEmergencyCount() {
        setProtectionModeEmergencyCount(0)
    }
    
    fun setProtectionModeLastEmergencyDate(date: String) {
        sharedPrefs.edit().putString(KEY_PROTECTION_MODE_LAST_EMERGENCY_DATE, date).apply()
    }
    
    fun getProtectionModeLastEmergencyDate(): String? {
        return sharedPrefs.getString(KEY_PROTECTION_MODE_LAST_EMERGENCY_DATE, null)
    }
    
    fun resetDailyEmergencyCountIfNeeded(): Boolean {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastEmergencyDate = getProtectionModeLastEmergencyDate()
        
        return if (today != lastEmergencyDate) {
            // New day - reset counter
            setProtectionModeEmergencyCount(0)
            setProtectionModeLastEmergencyDate(today)
            true
        } else {
            false
        }
    }
    
    fun isProtectionModeFullyConfigured(): Boolean {
        return hasWakePhrase() && hasBuddyContact() && isProtectionModeEnabled()
    }
    
    fun clearProtectionModeData() {
        sharedPrefs.edit()
            .remove(KEY_PROTECTION_MODE_START_TIME)
            .remove(KEY_PROTECTION_MODE_EMERGENCY_COUNT)
            .remove(KEY_PROTECTION_MODE_LAST_EMERGENCY_DATE)
            .apply()
    }
    
    fun clearSensitiveData() {
        // Clear sensitive data before factory reset
        sharedPrefs.edit()
            .remove(KEY_WAKE_PHRASE)
            .remove(KEY_WAKE_PHRASE_AUDIO)
            .remove(KEY_BUDDY_NAME)
            .remove(KEY_BUDDY_PHONE)
            .remove(KEY_PHRASE_DETECTIONS_LOG)
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .remove(KEY_BIOMETRIC_EMAIL)
            .remove(KEY_BIOMETRIC_PASSWORD)
            .remove(KEY_TWILIO_ACCOUNT_SID)
            .remove(KEY_TWILIO_AUTH_TOKEN)
            .remove(KEY_TWILIO_FROM_PHONE)
            .remove(KEY_AZURE_SPEECH_KEY)
            .remove(KEY_AZURE_SPEECH_REGION)
            .remove(KEY_PORCUPINE_ACCESS_KEY)
            .apply()
    }
    
    fun getProtectionModeStatus(): Map<String, Any> {
        val startTime = getProtectionModeStartTime()
        val isActive = isProtectionModeEnabled()
        val phrase = getWakePhrase()
        val emergencyContact = getBuddyPhone()
        val emergencyCount = getProtectionModeEmergencyCount()
        val duration = getProtectionModeDurationHours()
        
        return mapOf(
            "isActive" to isActive,
            "hasPhrase" to !phrase.isNullOrEmpty(),
            "phraseLength" to (phrase?.length ?: 0),
            "hasEmergencyContact" to !emergencyContact.isNullOrEmpty(),
            "emergencyCountToday" to emergencyCount,
            "protectionDurationHours" to duration,
            "startTime" to startTime,
            "isFullyConfigured" to isProtectionModeFullyConfigured()
        )
    }
    
    // ========================================
    // DOUBLE-CONFIRMATION STATE PERSISTENCE
    // ========================================
    
    /**
     * Save confirmation state for crash resilience
     * Stores the current double-confirmation progress in persistent storage
     */
    fun saveConfirmationState(
        count: Int, 
        firstTime: Long, 
        lastTime: Long, 
        keyword: String?, 
        detectorType: String?
    ) {
        sharedPrefs.edit()
            .putInt(KEY_CONFIRMATION_COUNT, count)
            .putLong(KEY_FIRST_CONFIRMATION_TIME, firstTime)
            .putLong(KEY_LAST_CONFIRMATION_TIME, lastTime)
            .putString(KEY_CONFIRMATION_KEYWORD, keyword)
            .putString(KEY_CONFIRMATION_DETECTOR_TYPE, detectorType)
            .apply()
    }
    
    /**
     * Load confirmation state after service restart/crash
     * Returns saved double-confirmation progress
     */
    fun loadConfirmationState(): ConfirmationState {
        return ConfirmationState(
            count = sharedPrefs.getInt(KEY_CONFIRMATION_COUNT, 0),
            firstTime = sharedPrefs.getLong(KEY_FIRST_CONFIRMATION_TIME, 0L),
            lastTime = sharedPrefs.getLong(KEY_LAST_CONFIRMATION_TIME, 0L),
            keyword = sharedPrefs.getString(KEY_CONFIRMATION_KEYWORD, null),
            detectorType = sharedPrefs.getString(KEY_CONFIRMATION_DETECTOR_TYPE, null)
        )
    }
    
    /**
     * Clear confirmation state (reset double-confirmation)
     */
    fun clearConfirmationState() {
        sharedPrefs.edit()
            .remove(KEY_CONFIRMATION_COUNT)
            .remove(KEY_FIRST_CONFIRMATION_TIME)
            .remove(KEY_LAST_CONFIRMATION_TIME)
            .remove(KEY_CONFIRMATION_KEYWORD)
            .remove(KEY_CONFIRMATION_DETECTOR_TYPE)
            .apply()
    }
    
    /**
     * Check if confirmation state is valid (within timeout window)
     */
    fun isConfirmationStateValid(timeoutMs: Long = 30000L): Boolean {
        val state = loadConfirmationState()
        if (state.count == 0) return false
        
        val currentTime = System.currentTimeMillis()
        return (currentTime - state.firstTime) <= timeoutMs
    }
    
    /**
     * Get readable confirmation status for debugging
     */
    fun getConfirmationStateStatus(): Map<String, Any> {
        val state = loadConfirmationState()
        val currentTime = System.currentTimeMillis()
        val isValid = isConfirmationStateValid()
        
        return mapOf(
            "count" to state.count,
            "keyword" to (state.keyword ?: "none"),
            "detectorType" to (state.detectorType ?: "none"),
            "firstTimeAgo" to if (state.firstTime > 0) (currentTime - state.firstTime) / 1000 else 0,
            "lastTimeAgo" to if (state.lastTime > 0) (currentTime - state.lastTime) / 1000 else 0,
            "isValid" to isValid,
            "needsReset" to (state.count > 0 && !isValid)
        )
    }
    
    /**
     * Data class for confirmation state
     */
    data class ConfirmationState(
        val count: Int,
        val firstTime: Long,
        val lastTime: Long,
        val keyword: String?,
        val detectorType: String?
    )
    
    // ========================================
    // AUDIO FEEDBACK CONTROL
    // ========================================
    
    /**
     * Enable/disable audio feedback from speech recognition
     * When disabled, prevents beeps and system sounds
     */
    fun setAudioFeedbackEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_AUDIO_FEEDBACK_ENABLED, enabled).apply()
    }
    
    fun isAudioFeedbackEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_AUDIO_FEEDBACK_ENABLED, false) // Default to disabled to prevent beeps
    }
    
    /**
     * Track if system sounds are currently muted by the app
     */
    fun setSystemSoundsMuted(muted: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_SYSTEM_SOUNDS_MUTED, muted).apply()
    }
    
    fun areSystemSoundsMuted(): Boolean {
        return sharedPrefs.getBoolean(KEY_SYSTEM_SOUNDS_MUTED, false)
    }
    
    /**
     * Emergency audio settings override for complete silence
     */
    fun enableSilentMode() {
        setAudioFeedbackEnabled(false)
        setSystemSoundsMuted(true)
    }
    
    fun disableSilentMode() {
        setAudioFeedbackEnabled(true)
        setSystemSoundsMuted(false)
    }
    
    fun isSilentModeEnabled(): Boolean {
        return !isAudioFeedbackEnabled() && areSystemSoundsMuted()
    }
}
