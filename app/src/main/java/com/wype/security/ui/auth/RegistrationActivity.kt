package com.wype.security.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.wype.security.data.PrefsLite
import com.wype.security.databinding.ActivityRegistrationBinding
import com.wype.security.ui.home.HomeActivity

class RegistrationActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRegistrationBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.registerBtn.setOnClickListener {
            val email = binding.emailEdit.text.toString().trim()
            if (email.isNotEmpty()) {
                PrefsLite.putRegistrationEmail(this, email)
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            }
        }
    }
}
