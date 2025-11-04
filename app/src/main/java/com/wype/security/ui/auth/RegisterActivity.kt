package com.wype.security.ui.auth

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.wype.security.databinding.ActivityRegisterBinding
import com.wype.security.ui.MainActivity
import com.wype.security.utils.PreferencesManager

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        preferencesManager = PreferencesManager(this)
        auth = FirebaseAuth.getInstance()
        
        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        // Register button
        binding.btnRegister.setOnClickListener {
            performRegistration()
        }
        
        // Back to login button
        binding.btnBackToLogin.setOnClickListener {
            finish() // Go back to login
        }
    }
    
    private fun performRegistration() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()
        
        if (email.isEmpty()) {
            binding.etEmail.error = "Email is required"
            binding.etEmail.requestFocus()
            return
        }
        
        if (password.isEmpty()) {
            binding.etPassword.error = "Password is required"
            binding.etPassword.requestFocus()
            return
        }
        
        if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            binding.etPassword.requestFocus()
            return
        }
        
        if (confirmPassword.isEmpty()) {
            binding.etConfirmPassword.error = "Please confirm password"
            binding.etConfirmPassword.requestFocus()
            return
        }
        
        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            binding.etConfirmPassword.requestFocus()
            return
        }
        
        // Show loading state
        Toast.makeText(this, "Creating account...", Toast.LENGTH_SHORT).show()
        
        // Firebase Authentication
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    val user = auth.currentUser
                    preferencesManager.setUserLoggedIn(true)
                    Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                    startMainActivity()
                } else {
                    // If sign up fails, display a detailed message to the user.
                    val errorMessage = task.exception?.message ?: "Unknown error"
                    val errorCode = when {
                        errorMessage.contains("email-already-in-use") -> "This email is already registered. Please login instead."
                        errorMessage.contains("invalid-email") -> "Invalid email format. Please check your email."
                        errorMessage.contains("weak-password") -> "Password is too weak. Please use a stronger password."
                        errorMessage.contains("too-many-requests") -> "Too many requests. Please try again later."
                        errorMessage.contains("network") -> "Network error. Please check your internet connection."
                        else -> "Registration failed: $errorMessage"
                    }
                    Toast.makeText(this, errorCode, Toast.LENGTH_LONG).show()
                    
                    // Also log the full error for debugging
                    android.util.Log.e("RegisterActivity", "Registration failed", task.exception)
                }
            }
    }
    
    
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun showButtonPressed() {
        // Show clicked state briefly on the register background
        binding.registerBackground.postDelayed({
            // You can add visual feedback here if needed
        }, 150)
    }
}
