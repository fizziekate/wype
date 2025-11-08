package com.wype.security.ui.home

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.wype.security.R
import com.wype.security.data.PrefsLite
import com.wype.security.databinding.ActivityHomeBinding
import com.wype.security.ui.backup.GoogleBackupActivity
import com.wype.security.util.ProvisioningEmail

class HomeActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityHomeBinding
    private var backupCompleted = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.backupBtn.setOnClickListener {
            startActivityForResult(
                Intent(this, GoogleBackupActivity::class.java),
                REQUEST_BACKUP
            )
        }
        
        binding.enableDoBtn.setOnClickListener {
            val email = PrefsLite.getRegistrationEmail(this)
            if (email != null) {
                val emailChooser = ProvisioningEmail.composeProvisioningEmail(this, email)
                startActivity(emailChooser)
                
                // Launch Settings after email chooser
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_BACKUP && resultCode == RESULT_OK) {
            backupCompleted = true
            // Change backup button image to clicked state
            binding.backupBtn.setBackgroundResource(R.drawable.do_google_backup_clicked)
        }
    }
    
    companion object {
        private const val REQUEST_BACKUP = 1001
    }
}
