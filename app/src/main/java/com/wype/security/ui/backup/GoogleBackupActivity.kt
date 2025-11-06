package com.wype.security.ui.backup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wype.security.R
import org.json.JSONObject

class GoogleBackupActivity : AppCompatActivity() {
    
    private lateinit var createBackupButton: Button
    
    companion object {
        private const val CREATE_BACKUP_REQUEST = 1001
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
        // Launch SAF ACTION_CREATE_DOCUMENT for a JSON file
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "wype_backup.json")
        }
        
        startActivityForResult(intent, CREATE_BACKUP_REQUEST)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == CREATE_BACKUP_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    // Create backup JSON
                    val backupJson = JSONObject().apply {
                        put("wype_backup", true)
                        put("timestamp", System.currentTimeMillis())
                    }
                    
                    // Write JSON to the URI
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(backupJson.toString().toByteArray())
                    }
                    
                    // Mark backup as completed
                    getSharedPreferences("wype_prefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("backup_completed", true)
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
    }
}
