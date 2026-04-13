package com.wype.security.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.model.File as DriveFile
import com.wype.security.admin.WypeDeviceAdminReceiver
import com.wype.security.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service to handle emergency backup to Google Drive before factory reset
 */
class GoogleBackupService : Service() {

    companion object {
        private const val TAG = "GoogleBackupService"
        private const val NOTIFICATION_ID = 5001
        const val ACTION_START_EMERGENCY_BACKUP = "START_EMERGENCY_BACKUP"
        const val ACTION_BACKUP_COMPLETE = "BACKUP_COMPLETE"
        const val ACTION_BACKUP_FAILED = "BACKUP_FAILED"
        private const val BACKUP_FOLDER_NAME = "WYPE Emergency Backup"
        private const val BACKUP_TIMEOUT = 30000L // 30 seconds timeout
    }

    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground immediately — Android 14+ requires the type in code too
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createSilentNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, createSilentNotification())
        }

        when (intent?.action) {
            ACTION_START_EMERGENCY_BACKUP -> {
                startEmergencyBackup()
            }
        }
        return START_NOT_STICKY
    }

    private fun createSilentNotification(): Notification {
        return NotificationCompat.Builder(this, com.wype.security.WypeApp.WYPE_PROTECTION_CHANNEL_ID)
            .setContentTitle("").setContentText("")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setShowWhen(false).setSilent(true).setLocalOnly(true)
            .setColor(Color.TRANSPARENT).build()
    }

    private fun startEmergencyBackup() {
        Log.w(TAG, "Starting emergency backup process")
        
        try {
            val googleAccount = GoogleSignIn.getLastSignedInAccount(this)
            
            if (googleAccount == null || !preferencesManager.isBackupEnabled()) {
                Log.i(TAG, "Backup not available or disabled - proceeding directly to factory reset")
                broadcastBackupResult(false, "Backup not configured or disabled")
                
                // Proceed directly to factory reset after short delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    triggerFactoryReset()
                    stopSelf()
                }, 2000) // 2 second delay
                return
            }
            
            // Perform actual Google Drive backup
            performGoogleDriveBackup(googleAccount)
            
        } catch (e: Exception) {
            Log.e(TAG, "Google backup failed: ${e.message}")
            broadcastBackupResult(false, "Google backup failed: ${e.message}")
            
            // Proceed directly to factory reset after short delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                triggerFactoryReset()
                stopSelf()
            }, 2000) // 2 second delay
        }
    }

    private fun createEmergencyBackupData(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        
        return buildString {
            appendLine("WYPE EMERGENCY BACKUP")
            appendLine("Generated: $timestamp")
            appendLine("Device: ${android.os.Build.MODEL} (${android.os.Build.MANUFACTURER})")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE}")
            appendLine()
            
            // Include app preferences (non-sensitive data only)
            appendLine("=== APP CONFIGURATION ===")
            appendLine("Wake phrase configured: ${preferencesManager.hasWakePhrase()}")
            appendLine("Buddy contact configured: ${preferencesManager.hasBuddyContact()}")
            appendLine("Device admin enabled: ${preferencesManager.isDeviceAdminEnabled()}")
            appendLine("Backup enabled: ${preferencesManager.isBackupEnabled()}")
            appendLine()
            
            // Include buddy contact info (for recovery)
            val buddyName = preferencesManager.getBuddyName()
            val buddyPhone = preferencesManager.getBuddyPhone()
            if (!buddyName.isNullOrEmpty() && !buddyPhone.isNullOrEmpty()) {
                appendLine("=== EMERGENCY CONTACT ===")
                appendLine("Name: $buddyName")
                appendLine("Phone: $buddyPhone")
                appendLine()
            }
            
            // Include detection log for forensics
            val detectionLog = preferencesManager.getDetectionLog()
            if (detectionLog.isNotEmpty()) {
                appendLine("=== DETECTION LOG ===")
                appendLine(detectionLog)
                appendLine()
            }
            
            appendLine("=== BACKUP NOTES ===")
            appendLine("This backup was created automatically by WYPE Security")
            appendLine("before performing emergency factory reset.")
            appendLine("Contact emergency buddy if you need assistance.")
            appendLine()
            appendLine("END OF BACKUP")
        }
    }
    
    private fun performGoogleDriveBackup(googleAccount: GoogleSignInAccount) {
        Log.i(TAG, "Starting Google Drive backup for account: ${googleAccount.email}")
        
        // Use coroutine for async operations
        val serviceScope = CoroutineScope(Dispatchers.IO)
        serviceScope.launch {
            try {
                // Create backup data
                val backupData = createEmergencyBackupData()
                
                // Setup Google Drive API client
                val credential = GoogleAccountCredential.usingOAuth2(
                    this@GoogleBackupService,
                    listOf(DriveScopes.DRIVE_FILE)
                )
                credential.selectedAccount = googleAccount.account
                
                val driveService = Drive.Builder(
                    AndroidHttp.newCompatibleTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                )
                    .setApplicationName("WYPE Security")
                    .build()
                
                // Create file metadata
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                val fileName = "WYPE_Emergency_Backup_$timestamp.txt"
                
                val fileMetadata = DriveFile().apply {
                    name = fileName
                    parents = listOf(getOrCreateBackupFolderId(driveService))
                }
                
                // Upload file to Google Drive
                val mediaContent = ByteArrayContent("text/plain", backupData.toByteArray())
                
                val uploadedFile = driveService.files()
                    .create(fileMetadata, mediaContent)
                    .setFields("id,name,createdTime")
                    .execute()
                
                Log.i(TAG, "Backup successfully uploaded to Google Drive: ${uploadedFile.name} (ID: ${uploadedFile.id})")
                
                // Update last backup time
                preferencesManager.setLastBackupTime(System.currentTimeMillis())
                
                // Notify success on main thread
                withContext(Dispatchers.Main) {
                    broadcastBackupResult(true, "Emergency backup completed successfully")
                    
                    // Trigger factory reset after successful backup
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        Log.w(TAG, "Google Drive backup complete - initiating factory reset")
                        triggerFactoryReset()
                        stopSelf()
                    }, 3000) // 3 second delay for backup confirmation
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Google Drive backup failed", e)
                
                // Notify failure on main thread
                withContext(Dispatchers.Main) {
                    broadcastBackupResult(false, "Google Drive backup failed: ${e.message}")
                    
                    // Proceed with factory reset even if backup failed
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        Log.w(TAG, "Backup failed but proceeding with factory reset")
                        triggerFactoryReset()
                        stopSelf()
                    }, 2000)
                }
            }
        }
    }
    
    private fun getOrCreateBackupFolderId(driveService: Drive): String {
        try {
            // Check if WYPE backup folder already exists
            val query = "name='$BACKUP_FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false"
            val result = driveService.files().list().setQ(query).execute()
            
            if (result.files.isNotEmpty()) {
                val folderId = result.files[0].id
                Log.d(TAG, "Using existing backup folder: $folderId")
                return folderId
            }
            
            // Create new folder
            val folderMetadata = DriveFile().apply {
                name = BACKUP_FOLDER_NAME
                mimeType = "application/vnd.google-apps.folder"
            }
            
            val folder = driveService.files().create(folderMetadata).setFields("id").execute()
            Log.d(TAG, "Created new backup folder: ${folder.id}")
            return folder.id
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create/find backup folder, using root directory", e)
            return "root"
        }
    }

    private fun broadcastBackupResult(success: Boolean, message: String) {
        val resultIntent = Intent().apply {
            action = if (success) ACTION_BACKUP_COMPLETE else ACTION_BACKUP_FAILED
            putExtra("message", message)
            putExtra("timestamp", System.currentTimeMillis())
        }
        sendBroadcast(resultIntent)
    }
    
    private fun triggerFactoryReset() {
        Log.w(TAG, "Triggering factory reset after backup completion")
        
        try {
            // Send intent to device admin receiver to perform factory reset
            val resetIntent = Intent("com.wype.security.FACTORY_RESET").apply {
                setClass(this@GoogleBackupService, WypeDeviceAdminReceiver::class.java)
            }
            sendBroadcast(resetIntent)
            
            Log.w(TAG, "Factory reset broadcast sent from backup service")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger factory reset from backup service", e)
            
            // Fallback - try direct device admin approach
            try {
                WypeDeviceAdminReceiver.performFactoryReset(this)
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Fallback factory reset from backup service also failed", fallbackError)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Google backup service destroyed")
    }
}
