package com.wype.security.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wype.security.R
import com.wype.security.data.PrefsLite
import com.wype.security.ui.home.HomeActivity

class RegistrationActivity : AppCompatActivity() {
    
    private lateinit var emailEditText: EditText
    private lateinit var registerButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)
        
        // Initialize PrefsLite
        PrefsLite.init(applicationContext)
        
        emailEditText = findViewById(R.id.email)
        registerButton = findViewById(R.id.register)
        
        registerButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter an email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Save email to PrefsLite
            PrefsLite.saveRegistrationEmail(email)
            
            // Start HomeActivity
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
