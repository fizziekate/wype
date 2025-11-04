package com.wype.security.ui.auth

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.wype.security.databinding.ActivityLoginBinding
import com.wype.security.ui.MainActivity
import com.wype.security.utils.PreferencesManager
import com.wype.security.utils.PermissionManager
import com.wype.security.utils.BiometricHelper

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var auth: FirebaseAuth
    private lateinit var biometricHelper: BiometricHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        preferencesManager = PreferencesManager(this)
        auth = FirebaseAuth.getInstance()
        biometricHelper = BiometricHelper(this)
        
        // Check if user is already logged in with Firebase
        if (auth.currentUser != null) {
            startMainActivity()
            return
        }
        
        // TEMPORARY: For testing, always bypass to MainActivity
        android.util.Log.d("LoginActivity", "TESTING: Bypassing authentication")
        Toast.makeText(this, "Testing: Bypassing auth", Toast.LENGTH_SHORT).show()
        startMainActivity()
        return
        
        setupClickListeners()
        setupBiometricLogin()
        testFirebaseConnection()
        addTemporaryTestButton()
    }
    
    private fun setupClickListeners() {
        android.util.Log.d("LoginActivity", "Setting up click listeners...")
        
        // Login button
        binding.btnLogin.setOnClickListener {
            android.util.Log.d("LoginActivity", "Login button clicked!")
            performLogin()
        }
        
        // Register button - navigate to register screen
        binding.btnRegister.setOnClickListener {
            android.util.Log.d("LoginActivity", "Register button clicked!")
            startRegisterActivity()
        }
        
        android.util.Log.d("LoginActivity", "Click listeners set up successfully")
    }
    
    private fun performLogin() {
        android.util.Log.d("LoginActivity", "performLogin() called")
        
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        
        android.util.Log.d("LoginActivity", "Email entered: '${email.take(3)}...${email.takeLast(3)}' (length: ${email.length})")
        android.util.Log.d("LoginActivity", "Password entered: [${password.length} characters]")
        
        if (email.isEmpty()) {
            android.util.Log.w("LoginActivity", "Email is empty")
            binding.etEmail.error = "Email is required"
            binding.etEmail.requestFocus()
            Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (password.isEmpty()) {
            android.util.Log.w("LoginActivity", "Password is empty")
            binding.etPassword.error = "Password is required"
            binding.etPassword.requestFocus()
            Toast.makeText(this, "Please enter your password", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Validate email format
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            android.util.Log.w("LoginActivity", "Invalid email format: $email")
            binding.etEmail.error = "Please enter a valid email address"
            binding.etEmail.requestFocus()
            Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show()
            return
        }
        
        android.util.Log.d("LoginActivity", "Starting Firebase authentication...")
        // Show loading state
        Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show()
        
        // Firebase Authentication
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    android.util.Log.d("LoginActivity", "Login successful! User: ${auth.currentUser?.email}")
                    preferencesManager.setUserLoggedIn(true)
                    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                    
                    // Offer biometric setup if available and not already enabled
                    if (biometricHelper.isBiometricAvailable() == BiometricHelper.BiometricAvailability.AVAILABLE && 
                        !preferencesManager.isBiometricLoginEnabled()) {
                        offerBiometricSetup(email, password)
                    } else {
                        startMainActivity()
                    }
                } else {
                    // If sign in fails, display a detailed message to the user.
                    val errorMessage = task.exception?.message ?: "Unknown error"
                    val errorCode = when {
                        errorMessage.contains("no user record") -> "No account found with this email. Please register first."
                        errorMessage.contains("wrong-password") -> "Incorrect password. Please try again."
                        errorMessage.contains("invalid-email") -> "Invalid email format. Please check your email."
                        errorMessage.contains("user-disabled") -> "This account has been disabled."
                        errorMessage.contains("too-many-requests") -> "Too many failed attempts. Please try again later."
                        errorMessage.contains("network") -> "Network error. Please check your internet connection."
                        else -> "Login failed: $errorMessage"
                    }
                    Toast.makeText(this, errorCode, Toast.LENGTH_LONG).show()
                    
                    // Also log the full error for debugging
                    android.util.Log.e("LoginActivity", "Login failed", task.exception)
                }
            }
    }
    
    
    private fun startRegisterActivity() {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
    }
    
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun testFirebaseConnection() {
        try {
            android.util.Log.d("LoginActivity", "Testing Firebase connection...")
            android.util.Log.d("LoginActivity", "Firebase Auth instance: $auth")
            android.util.Log.d("LoginActivity", "Current user: ${auth.currentUser}")
            android.util.Log.d("LoginActivity", "Firebase App: ${com.google.firebase.FirebaseApp.getInstance()}")
        } catch (e: Exception) {
            android.util.Log.e("LoginActivity", "Firebase connection test failed", e)
        }
    }
    
    private fun addTemporaryTestButton() {
        // Add a temporary test to see if login process works with hardcoded values
        binding.loginBackground.setOnLongClickListener {
            android.util.Log.d("LoginActivity", "Background long-clicked - testing login with test@example.com")
            testLoginWithHardcodedValues()
            true
        }
        
        // Add quick bypass for testing - double tap to go directly to MainActivity
        var tapCount = 0
        binding.loginBackground.setOnClickListener {
            tapCount++
            Handler(Looper.getMainLooper()).postDelayed({
                if (tapCount >= 2) {
                    android.util.Log.d("LoginActivity", "Double tap detected - bypassing auth for testing")
                    Toast.makeText(this, "Bypassing auth for testing", Toast.LENGTH_SHORT).show()
                    startMainActivity()
                }
                tapCount = 0
            }, 500)
        }
    }
    
    private fun testLoginWithHardcodedValues() {
        android.util.Log.d("LoginActivity", "Testing login with hardcoded test values...")
        
        // Test Firebase connection with a common test email
        auth.signInWithEmailAndPassword("test@example.com", "password123")
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    android.util.Log.d("LoginActivity", "Test login successful!")
                    Toast.makeText(this, "Test login successful!", Toast.LENGTH_SHORT).show()
                } else {
                    val errorMessage = task.exception?.message ?: "Unknown error"
                    android.util.Log.e("LoginActivity", "Test login failed: $errorMessage", task.exception)
                    Toast.makeText(this, "Test login failed: $errorMessage", Toast.LENGTH_LONG).show()
                }
            }
    }
    
    private fun setupBiometricLogin() {
        val biometricAvailability = biometricHelper.isBiometricAvailable()
        
        when (biometricAvailability) {
            BiometricHelper.BiometricAvailability.AVAILABLE -> {
                // Show fingerprint button and enable biometric login
                binding.fabFingerprint.visibility = android.view.View.VISIBLE
                android.util.Log.d("LoginActivity", "Biometric authentication available")
                
                // Set up fingerprint button click listener
                binding.fabFingerprint.setOnClickListener {
                    performBiometricLogin()
                }
                
                // Auto-prompt biometric login if user has logged in before with biometrics
                if (preferencesManager.isBiometricLoginEnabled()) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        performBiometricLogin()
                    }, 500) // Small delay to let UI settle
                }
            }
            BiometricHelper.BiometricAvailability.NONE_ENROLLED -> {
                // Show fingerprint button but with enrollment message
                binding.fabFingerprint.visibility = android.view.View.VISIBLE
                binding.fabFingerprint.setOnClickListener {
                    Toast.makeText(this, getString(com.wype.security.R.string.biometric_not_enrolled), Toast.LENGTH_LONG).show()
                }
                android.util.Log.w("LoginActivity", "Biometric hardware available but no fingerprints enrolled")
            }
            else -> {
                // Hide fingerprint button if biometric is not available
                binding.fabFingerprint.visibility = android.view.View.GONE
                android.util.Log.w("LoginActivity", "Biometric authentication not available: ${biometricHelper.getBiometricStatusMessage()}")
            }
        }
    }
    
    private fun performBiometricLogin() {
        android.util.Log.d("LoginActivity", "Starting biometric authentication")
        
        // Check if user has saved login credentials for biometric login
        val savedEmail = preferencesManager.getBiometricLoginEmail()
        
        if (savedEmail.isNullOrEmpty()) {
            // First time biometric setup - need to validate password first
            showBiometricSetupDialog()
            return
        }
        
        // Perform biometric authentication
        biometricHelper.authenticate(
            title = getString(com.wype.security.R.string.biometric_login_title),
            subtitle = getString(com.wype.security.R.string.biometric_login_subtitle),
            negativeButtonText = getString(com.wype.security.R.string.biometric_fallback_button),
            onSuccess = {
                android.util.Log.i("LoginActivity", "Biometric authentication successful")
                Toast.makeText(this, getString(com.wype.security.R.string.biometric_success), Toast.LENGTH_SHORT).show()
                loginWithSavedCredentials(savedEmail)
            },
            onError = { errorMessage ->
                android.util.Log.e("LoginActivity", "Biometric authentication error: $errorMessage")
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            },
            onCancel = {
                android.util.Log.d("LoginActivity", "Biometric authentication cancelled")
                // User can still use password login
            }
        )
    }
    
    private fun showBiometricSetupDialog() {
        AlertDialog.Builder(this)
            .setTitle("Set Up Fingerprint Login")
            .setMessage("To use fingerprint login, please enter your login credentials first. This will securely link your fingerprint to your account.")
            .setPositiveButton("Set Up") { _, _ ->
                // User needs to login with password first to enable biometric
                Toast.makeText(this, "Please login with your password to enable fingerprint login", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun loginWithSavedCredentials(email: String) {
        android.util.Log.d("LoginActivity", "Logging in with saved credentials for biometric user")
        
        // Get saved password (encrypted/hashed)
        val savedPassword = preferencesManager.getBiometricLoginPassword()
        
        if (savedPassword.isNullOrEmpty()) {
            android.util.Log.e("LoginActivity", "No saved password for biometric login")
            Toast.makeText(this, "Biometric login setup incomplete. Please login with password.", Toast.LENGTH_LONG).show()
            return
        }
        
        // Perform Firebase login with saved credentials
        auth.signInWithEmailAndPassword(email, savedPassword)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    android.util.Log.d("LoginActivity", "Biometric login successful! User: ${auth.currentUser?.email}")
                    preferencesManager.setUserLoggedIn(true)
                    Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
                    startMainActivity()
                } else {
                    android.util.Log.e("LoginActivity", "Biometric login failed", task.exception)
                    Toast.makeText(this, "Biometric login failed. Please use password login.", Toast.LENGTH_LONG).show()
                    
                    // Clear saved biometric credentials if they're invalid
                    preferencesManager.clearBiometricLoginCredentials()
                }
            }
    }
    
    private fun offerBiometricSetup(email: String, password: String) {
        AlertDialog.Builder(this)
            .setTitle("Enable Fingerprint Login?")
            .setMessage("Would you like to enable fingerprint login for faster access to WYPE Security?")
            .setPositiveButton("Enable") { _, _ ->
                enableBiometricForCurrentLogin(email, password)
            }
            .setNegativeButton("Not Now") { _, _ ->
                startMainActivity()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun enableBiometricForCurrentLogin(email: String, password: String) {
        android.util.Log.d("LoginActivity", "Enabling biometric login for user: $email")
        
        biometricHelper.authenticate(
            title = "Enable Fingerprint Login",
            subtitle = "Use your fingerprint to confirm biometric login setup",
            negativeButtonText = "Skip",
            onSuccess = {
                // Save credentials for future biometric login
                preferencesManager.setBiometricLoginCredentials(email, password)
                preferencesManager.setBiometricLoginEnabled(true)
                
                Toast.makeText(this, "Fingerprint login enabled!", Toast.LENGTH_SHORT).show()
                android.util.Log.i("LoginActivity", "Biometric login enabled for user")
                
                // Proceed to main activity after setup
                startMainActivity()
            },
            onError = { errorMessage ->
                android.util.Log.e("LoginActivity", "Failed to enable biometric: $errorMessage")
                Toast.makeText(this, "Could not enable fingerprint login: $errorMessage", Toast.LENGTH_LONG).show()
            },
            onCancel = {
                android.util.Log.d("LoginActivity", "User skipped biometric setup")
                // Continue with normal login
                startMainActivity()
            }
        )
    }
    
    private fun showButtonPressed() {
        // Show clicked state briefly on the login background
        binding.loginBackground.postDelayed({
            // You can add visual feedback here if needed
        }, 150)
    }
}
