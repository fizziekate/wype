package com.wype.security.ui.home

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wype.security.R
import com.wype.security.data.PrefsLite
import com.wype.security.ui.backup.GoogleBackupActivity
import com.wype.security.util.ProvisioningEmail
import java.io.File

class HomeActivity : AppCompatActivity() {
    
    private lateinit var backgroundImageView: ImageView
    private lateinit var backupButton: Button
    private lateinit var enableDoButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        
        // Initialize PrefsLite
        PrefsLite.init(applicationContext)
        
        backgroundImageView = findViewById(R.id.backgroundImage)
        backupButton = findViewById(R.id.backupButton)
        enableDoButton = findViewById(R.id.enableDoButton)
        
        backupButton.setOnClickListener {
            // Start GoogleBackupActivity
            val intent = Intent(this, GoogleBackupActivity::class.java)
            startActivity(intent)
        }
        
        enableDoButton.setOnClickListener {
            enableDeviceOwner()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Check if backup was completed and update image
        val backupCompleted = getSharedPreferences("wype_prefs", MODE_PRIVATE)
            .getBoolean("backup_completed", false)
        if (backupCompleted) {
            backgroundImageView.setImageResource(R.drawable.do_google_backup_clicked)
        }
    }
    
    private fun enableDeviceOwner() {
        // Load registered email from PrefsLite
        val email = PrefsLite.getRegistrationEmail()
        
        if (email.isNullOrEmpty()) {
            Toast.makeText(this, "No registered email found", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Generate provisioning QR bitmap for component
            val componentName = "com.wype.security/.provisioning.DeviceAdminRcvr"
            val qrBitmap: Bitmap = ProvisioningEmail.generateProvisioningQrBitmap(componentName)
            
            // Write QR to cache
            val qrFile: File = ProvisioningEmail.writeQrToCache(this, qrBitmap)
            
            // Send email with QR code
            ProvisioningEmail.sendProvisioningEmail(this, email, qrFile)
            
            // Open Settings after starting email intent
            val settingsIntent = Intent(Settings.ACTION_SETTINGS)
            startActivity(settingsIntent)
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error generating QR code: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
}
