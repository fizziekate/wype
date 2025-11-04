package com.wype.security.ui.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wype.security.R
import com.wype.security.databinding.FragmentBackupBinding
import com.wype.security.service.GoogleBackupService

class BackupFragment : Fragment() {

    companion object {
        private const val TAG = "BackupFragment"
    }

    private var _binding: FragmentBackupBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var backupViewModel: BackupViewModel
    private var googleSignInClient: GoogleSignInClient? = null
    
    // Google Sign-In launcher
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleSignInResult(task)
        } else {
            Log.w(TAG, "Google sign-in cancelled or failed")
            // Toast removed to maintain clean UI design
        }
    }
    
    // Backup result receiver
    private val backupReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                GoogleBackupService.ACTION_BACKUP_COMPLETE -> {
                    val message = intent.getStringExtra("message") ?: "Backup completed"
                    handleBackupComplete(message)
                }
                GoogleBackupService.ACTION_BACKUP_FAILED -> {
                    val message = intent.getStringExtra("message") ?: "Backup failed"
                    handleBackupFailed(message)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        backupViewModel = ViewModelProvider(this)[BackupViewModel::class.java]
        _binding = FragmentBackupBinding.inflate(inflater, container, false)
        
        setupGoogleSignIn()
        setupObservers()
        setupClickListeners()
        registerBackupReceiver()
        
        return binding.root
    }
    
    override fun onResume() {
        super.onResume()
        // Check current Google account status
        checkGoogleAccountStatus()
        backupViewModel.refreshBackupStatus()
    }
    
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
            
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
    }
    
    private fun setupObservers() {
        backupViewModel.isBackupEnabled.observe(viewLifecycleOwner) { enabled ->
            updateBackupToggleUI(enabled)
        }
        
        backupViewModel.googleAccount.observe(viewLifecycleOwner) { account ->
            updateAccountDisplay(account)
        }
        
        backupViewModel.lastBackupTime.observe(viewLifecycleOwner) { timestamp ->
            updateLastBackupDisplay(timestamp)
        }
        
        backupViewModel.backupStatus.observe(viewLifecycleOwner) { status ->
            updateBackupStatusDisplay(status)
        }
    }
    
    private fun setupClickListeners() {
        binding.switchEnableBackup.setOnCheckedChangeListener { _, isChecked ->
            handleBackupToggle(isChecked)
        }
        
        binding.btnSignInGoogle.setOnClickListener {
            showButtonPressed()
            signInToGoogle()
        }
        
        binding.btnSignOutGoogle.setOnClickListener {
            showButtonPressed()
            signOutFromGoogle()
        }
        
        binding.btnManualBackup.setOnClickListener {
            showButtonPressed()
            performManualBackup()
        }
        
        binding.btnTestBackup.setOnClickListener {
            showButtonPressed()
            testBackupFunctionality()
        }
    }
    
    private fun registerBackupReceiver() {
        val filter = IntentFilter().apply {
            addAction(GoogleBackupService.ACTION_BACKUP_COMPLETE)
            addAction(GoogleBackupService.ACTION_BACKUP_FAILED)
        }
        // Use RECEIVER_NOT_EXPORTED for Android 14+ compatibility
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireActivity().registerReceiver(
                backupReceiver, 
                filter, 
                android.content.Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            requireActivity().registerReceiver(backupReceiver, filter)
        }
    }
    
    private fun checkGoogleAccountStatus() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        backupViewModel.setGoogleAccount(account)
    }
    
    private fun handleBackupToggle(enabled: Boolean) {
        if (enabled) {
            val account = GoogleSignIn.getLastSignedInAccount(requireContext())
            if (account == null) {
                // Need to sign in first
                binding.switchEnableBackup.isChecked = false
                showGoogleSignInRequiredDialog()
                return
            }
        }
        
        backupViewModel.setBackupEnabled(enabled)
        
        // Toast messages removed to maintain clean UI design
        // Backup state is shown through UI elements instead
    }
    
    private fun showGoogleSignInRequiredDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Google Account Required")
            .setMessage("You need to sign in to your Google account to enable automatic backup functionality.")
            .setPositiveButton("Sign In") { _, _ ->
                signInToGoogle()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun signInToGoogle() {
        val signInIntent = googleSignInClient?.signInIntent
        if (signInIntent != null) {
            signInLauncher.launch(signInIntent)
        } else {
            // Error logging instead of toast for clean UI
            Log.e(TAG, "Error initializing Google sign-in")
        }
    }
    
    private fun signOutFromGoogle() {
        googleSignInClient?.signOut()
            ?.addOnCompleteListener(requireActivity()) {
                backupViewModel.setGoogleAccount(null)
                backupViewModel.setBackupEnabled(false)
                // Toast removed to maintain clean UI design
                Log.i(TAG, "Signed out from Google")
            }
    }
    
    private fun handleSignInResult(task: com.google.android.gms.tasks.Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult()
            backupViewModel.setGoogleAccount(account)
            // Toast removed to maintain clean UI design
            Log.d(TAG, "Google sign-in successful: ${account.email}")
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failed", e)
            // Toast removed to maintain clean UI design
        }
    }
    
    private fun performManualBackup() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account == null) {
            // Toast removed to maintain clean UI design
            Log.w(TAG, "Manual backup requested but no Google account signed in")
            return
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Manual Backup")
            .setMessage("Create a backup of your WYPE configuration to Google Drive now?")
            .setPositiveButton("Backup") { _, _ ->
                startBackupService()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun testBackupFunctionality() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account == null) {
            // Toast removed to maintain clean UI design
            Log.w(TAG, "Test backup requested but no Google account signed in")
            return
        }
        
        // Toast removed to maintain clean UI design
        Log.i(TAG, "Testing backup connection...")
        
        // This will test the backup system without triggering emergency
        startBackupService()
    }
    
    private fun startBackupService() {
        binding.progressBackup.visibility = View.VISIBLE
        binding.tvBackupStatus.text = "Creating backup..."
        
        val intent = Intent(requireContext(), GoogleBackupService::class.java).apply {
            action = GoogleBackupService.ACTION_START_EMERGENCY_BACKUP
        }
        requireContext().startService(intent)
    }
    
    private fun handleBackupComplete(message: String) {
        binding.progressBackup.visibility = View.GONE
        binding.tvBackupStatus.text = "Last backup: Now"
        backupViewModel.setLastBackupTime(System.currentTimeMillis())
        
        // Toast removed to maintain clean UI design
        Log.i(TAG, "Backup completed: $message")
    }
    
    private fun handleBackupFailed(message: String) {
        binding.progressBackup.visibility = View.GONE
        binding.tvBackupStatus.text = "Backup failed"
        binding.tvBackupStatus.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.emergency_red)
        )
        
        // Toast removed to maintain clean UI design
        Log.e(TAG, "Backup failed: $message")
    }
    
    private fun updateBackupToggleUI(enabled: Boolean) {
        binding.switchEnableBackup.isChecked = enabled
        binding.btnManualBackup.isEnabled = enabled
        binding.btnTestBackup.isEnabled = enabled
    }
    
    private fun updateAccountDisplay(account: GoogleSignInAccount?) {
        if (account != null) {
            binding.tvGoogleAccount.text = account.email
            binding.tvGoogleAccount.visibility = View.VISIBLE
            binding.btnSignInGoogle.visibility = View.GONE
            binding.btnSignOutGoogle.visibility = View.VISIBLE
        } else {
            binding.tvGoogleAccount.visibility = View.GONE
            binding.btnSignInGoogle.visibility = View.VISIBLE
            binding.btnSignOutGoogle.visibility = View.GONE
        }
    }
    
    private fun updateLastBackupDisplay(timestamp: Long?) {
        if (timestamp != null && timestamp > 0) {
            val date = java.util.Date(timestamp)
            val format = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            binding.tvLastBackup.text = "Last backup: ${format.format(date)}"
        } else {
            binding.tvLastBackup.text = "No backup yet"
        }
    }
    
    private fun updateBackupStatusDisplay(status: String?) {
        binding.tvBackupStatus.text = status ?: "Ready for backup"
        binding.tvBackupStatus.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.gray)
        )
    }
    
    private fun showButtonPressed() {
        // Show clicked version temporarily
        binding.backgroundImage.setImageResource(R.drawable.google_backup_clicked)
        
        // Revert to unclicked after 150ms
        Handler(Looper.getMainLooper()).postDelayed({
            binding.backgroundImage.setImageResource(R.drawable.google_backup_unclicked)
        }, 150)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        try {
            requireActivity().unregisterReceiver(backupReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering backup receiver", e)
        }
        
        _binding = null
    }
}
