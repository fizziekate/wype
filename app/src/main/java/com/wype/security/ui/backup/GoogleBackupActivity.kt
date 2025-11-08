package com.wype.security.ui.backup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.wype.security.databinding.ActivityGoogleBackupBinding
import org.json.JSONObject

class GoogleBackupActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityGoogleBackupBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGoogleBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.createBackupBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "wype_backup.json")
            }
            startActivityForResult(intent, REQUEST_CREATE_DOCUMENT)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CREATE_DOCUMENT && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val json = JSONObject().apply {
                            put("wype_backup", true)
                        }
                        outputStream.write(json.toString().toByteArray())
                    }
                    setResult(Activity.RESULT_OK)
                    finish()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    companion object {
        private const val REQUEST_CREATE_DOCUMENT = 2001
    }
}
