package com.wype.security.ui.backup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.wype.security.R
import org.json.JSONObject

class GoogleBackupActivity : AppCompatActivity() {
    
    private lateinit var createBackupButton: Button
    
    companion object {
        private const val PREFS_NAME = "wype_prefs"
        private const val KEY_BACKUP_COMPLETED = "backup_completed"
    }
    
    private val createBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                // Create backup JSON
                val backupJson = JSONObject().apply {
                    put("wype_backup", true)
                    put("timestamp", System.currentTimeMillis())
                }
                
                // Write JSON to the URI
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(backupJson.toString().toByteArray())
                }
                
                // Mark backup as completed
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_BACKUP_COMPLETED, true)
                    .apply()
                
                Toast.makeText(this, "Backup created successfully", Toast.LENGTH_SHORT).show()
                
                // Finish activity to return to home
                finish()
                
            } catch (e: Exception) {
                Toast.makeText(this, "Error creating backup: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_google_backup)
        
        createBackupButton = findViewById(R.id.createBackupButton)
        
        createBackupButton.setOnClickListener {
            createBackup()
        }
    }
    
    private fun createBackup() {
        // Launch SAF document picker with suggested filename
        createBackupLauncher.launch("wype_backup.json")
    }
}
