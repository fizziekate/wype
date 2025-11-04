package com.wype.security.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wype.security.R
import com.wype.security.databinding.FragmentHomeBinding
import com.wype.security.utils.PreferencesManager
import android.widget.EditText
import android.widget.SeekBar
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import com.wype.security.utils.AccessibilityHelper
import com.wype.security.service.ProtectionModeManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var protectionModeManager: ProtectionModeManager
    private lateinit var preferencesManager: PreferencesManager
    
    // Protection status monitoring
    private var statusUpdateJob: Job? = null
    private val statusUpdateInterval = 2000L // 2 seconds
    
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            homeViewModel.toggleService()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Initialize ProtectionModeService components
        initializeProtectionMode()
        
        setupObservers()
        setupClickListeners()
        
        // Start protection status monitoring
        startProtectionStatusMonitoring()

        return root
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh setup status when returning to this screen
        homeViewModel.refreshSetupStatus()
        
        // Check if wake phrase is configured and show reminder if not
        checkAndShowPhraseReminder()
        
        // Resume protection status monitoring
        startProtectionStatusMonitoring()
        updateProtectionModeStatus()
    }
    
    override fun onPause() {
        super.onPause()
        stopProtectionStatusMonitoring()
    }
    
    private fun checkAndShowPhraseReminder() {
        val setupStatus = homeViewModel.setupStatus.value
        if (setupStatus?.hasWakePhrase != true) {
            showPhraseReminderDialog()
        }
    }
    
    private fun showPhraseReminderDialog() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Wake Phrase Setup")
            .setMessage("You haven't recorded your WYPE wake phrase yet. Would you like to set it up now?")
            .setPositiveButton("Record Now") { _, _ ->
                findNavController().navigate(R.id.navigation_record)
            }
            .setNegativeButton("Later", null)
            .setCancelable(true)
            .create()
        
        dialog.show()
        
        // Change button colors to lighter colors after dialog is shown
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.light_gray)
        )
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.light_gray)
        )
    }
    
    private fun setupObservers() {
        homeViewModel.serviceStatus.observe(viewLifecycleOwner) { isRunning ->
            updateServiceStatus(isRunning)
        }
        
        homeViewModel.isListening.observe(viewLifecycleOwner) { isListening ->
            updateListeningStatus(isListening)
        }
        
        homeViewModel.setupStatus.observe(viewLifecycleOwner) { setupStatus ->
            updateSetupStatus(setupStatus)
        }
    }
    
    private fun setupClickListeners() {
        binding.btnToggleService.setOnClickListener {
            showButtonPressed()
            
            // Prioritize ProtectionModeService over legacy services
            val isProtectionActive = preferencesManager.isProtectionModeEnabled()
            if (isProtectionActive) {
                showStopProtectionDialog()
            } else {
                showStartProtectionDialog()
            }
        }
        
        
        // Quick setup navigation - safe null checks for hidden grid
        try {
            binding.gridQuickSetup.getChildAt(0)?.setOnClickListener {
                // Navigate to Record screen
                findNavController().navigate(R.id.navigation_record)
            }
            
            binding.gridQuickSetup.getChildAt(1)?.setOnClickListener {
                // Navigate to Buddy screen
                findNavController().navigate(R.id.navigation_buddy)
            }
            
            binding.gridQuickSetup.getChildAt(2)?.setOnClickListener {
                // Navigate to Instructions for device admin setup
                findNavController().navigate(R.id.navigation_instructions)
            }
            
            binding.gridQuickSetup.getChildAt(3)?.setOnClickListener {
                // Navigate to Backup screen
                findNavController().navigate(R.id.navigation_backup)
            }
        } catch (e: Exception) {
            // Grid is hidden in custom design, navigation handled by bottom nav
        }
        
        // Long-press listener for advanced configuration (and protection status)
        binding.backgroundImage.setOnLongClickListener {
            showProtectionStatusDialog()
            true
        }
    }
    
    private fun handleServiceToggle() {
        // Check if microphone permission is granted
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permission
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        
        // Check if accessibility service is enabled (preferred method)
        if (!AccessibilityHelper.isAccessibilityServiceEnabled(requireContext())) {
            showAccessibilityServiceDialog()
            return
        }
        
        // Check if wake phrase is configured
        val setupStatus = homeViewModel.setupStatus.value
        if (setupStatus?.hasWakePhrase != true) {
            showSetupRequiredDialog("Wake phrase not configured. Please set up your wake phrase first.")
            return
        }
        
        // All checks passed, use accessibility service for enhanced reliability
        toggleAccessibilityService()
    }
    
    private fun showAccessibilityServiceDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Enhanced Protection Available")
            .setMessage(
                "For maximum reliability, WYPE can run as an accessibility service with:\n\n" +
                "• Better background operation\n" +
                "• System-level permissions\n" +
                "• Enhanced battery optimization resistance\n\n" +
                "Would you like to enable enhanced protection?"
            )
            .setPositiveButton("Enable Enhanced Protection") { _, _ ->
                AccessibilityHelper.showEnableAccessibilityDialog(requireContext()) {
                    // User was directed to settings
                    showAccessibilityInstructions()
                }
            }
            .setNeutralButton("Use Standard Mode") { _, _ ->
                // Fallback to standard service
                homeViewModel.toggleService()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showAccessibilityInstructions() {
        val instructions = AccessibilityHelper.getSetupInstructions(requireContext())
        val instructionText = instructions.joinToString("\n")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Setup Instructions")
            .setMessage(
                "Please follow these steps:\n\n$instructionText\n\n" +
                "Once enabled, return to WYPE and the enhanced protection will start automatically."
            )
            .setPositiveButton("Got it", null)
            .show()
    }
    
    private fun toggleAccessibilityService() {
        val isCurrentlyRunning = homeViewModel.serviceStatus.value ?: false
        
        if (isCurrentlyRunning) {
            // Stop accessibility service
            AccessibilityHelper.stopWakeWordDetection(requireContext())
            updateServiceStatus(false)
            updateListeningStatus(false)
        } else {
            // Start accessibility service
            if (AccessibilityHelper.startWakeWordDetection(requireContext())) {
                updateServiceStatus(true)
                updateListeningStatus(true)
                
                // Show confirmation
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Enhanced Protection Active")
                    .setMessage("WYPE accessibility service is now running with enhanced reliability and system-level access.")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                showSetupRequiredDialog("Failed to start accessibility service. Please ensure it's enabled in settings.")
            }
        }
    }
    
    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.permission_microphone_title))
            .setMessage(getString(R.string.permission_microphone_message))
            .setPositiveButton(getString(R.string.ok)) { _, _ -> }
            .show()
    }
    
    private fun showSetupRequiredDialog(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Setup Required")
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok)) { _, _ -> }
            .show()
    }
    
    private fun updateServiceStatus(isRunning: Boolean) {
        if (isRunning) {
            binding.tvServiceStatus.text = "Service Running"
            binding.tvServiceStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_active))
            binding.btnToggleService.text = getString(R.string.home_stop_service)
        } else {
            binding.tvServiceStatus.text = "Service Stopped"
            binding.tvServiceStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_inactive))
            binding.btnToggleService.text = getString(R.string.home_start_service)
        }
    }
    
    private fun updateListeningStatus(isListening: Boolean) {
        binding.tvListeningStatus.text = if (isListening) {
            getString(R.string.home_status_listening)
        } else {
            getString(R.string.home_status_not_listening)
        }
    }
    
    private fun updateSetupStatus(setupStatus: HomeViewModel.SetupStatus) {
        // Only enable the service toggle if wake phrase is configured
        binding.btnToggleService.isEnabled = setupStatus.hasWakePhrase
    }
    
    
    private fun showButtonPressed() {
        // Show clicked version temporarily
        binding.backgroundImage.setImageResource(R.drawable.home_clicked)
        
        // Revert to unclicked after 150ms
        Handler(Looper.getMainLooper()).postDelayed({
            binding.backgroundImage.setImageResource(R.drawable.home_unclicked)
        }, 150)
    }
    
    private fun showPorcupineConfigurationDialog() {
        val preferencesManager = PreferencesManager(requireContext())
        
        // Create dialog layout
        val dialogLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // Access Key field
        val accessKeyLabel = TextView(requireContext()).apply {
            text = "Porcupine Access Key:"
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }
        val accessKeyField = EditText(requireContext()).apply {
            hint = "Enter your Picovoice access key"
            setText(preferencesManager.getPorcupineAccessKey() ?: "")
        }
        
        // Wake Word selection
        val wakeWordLabel = TextView(requireContext()).apply {
            text = "Wake Word:"
            textSize = 16f
            setPadding(0, 24, 0, 8)
        }
        
        val wakeWords = arrayOf(
            "picovoice", "bumblebee", "computer", "hey google", "hey siri",
            "jarvis", "alexa", "americano", "blueberry", "terminator"
        )
        
        val wakeWordSpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, wakeWords).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            
            // Set current selection
            val currentWakeWord = preferencesManager.getPorcupineWakeWord() ?: "picovoice"
            val currentIndex = wakeWords.indexOf(currentWakeWord)
            if (currentIndex >= 0) setSelection(currentIndex)
        }
        
        // Sensitivity slider
        val sensitivityLabel = TextView(requireContext()).apply {
            text = "Sensitivity: ${String.format("%.1f", preferencesManager.getPorcupineSensitivity())}"
            textSize = 16f
            setPadding(0, 24, 0, 8)
        }
        
        val sensitivitySeekBar = SeekBar(requireContext()).apply {
            max = 100
            progress = (preferencesManager.getPorcupineSensitivity() * 100).toInt()
            
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val sensitivity = progress / 100f
                    sensitivityLabel.text = "Sensitivity: ${String.format("%.1f", sensitivity)}"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        
        // Enable/Disable toggle info
        val statusLabel = TextView(requireContext()).apply {
            text = if (preferencesManager.isPorcupineEnabled()) {
                "🟢 Porcupine wake word detection is ENABLED"
            } else {
                "🔴 Porcupine wake word detection is DISABLED"
            }
            textSize = 14f
            setPadding(0, 24, 0, 0)
        }
        
        // Add views to layout
        dialogLayout.apply {
            addView(accessKeyLabel)
            addView(accessKeyField)
            addView(wakeWordLabel)
            addView(wakeWordSpinner)
            addView(sensitivityLabel)
            addView(sensitivitySeekBar)
            addView(statusLabel)
        }
        
        // Show dialog
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🎯 Porcupine Wake Word Configuration")
            .setView(dialogLayout)
            .setPositiveButton("Save & Enable") { _, _ ->
                val accessKey = accessKeyField.text.toString().trim()
                val selectedWakeWord = wakeWords[wakeWordSpinner.selectedItemPosition]
                val sensitivity = sensitivitySeekBar.progress / 100f
                
                if (accessKey.isNotEmpty()) {
                    // Save configuration
                    preferencesManager.setPorcupineAccessKey(accessKey)
                    preferencesManager.setPorcupineWakeWord(selectedWakeWord)
                    preferencesManager.setPorcupineSensitivity(sensitivity)
                    preferencesManager.setPorcupineEnabled(true)
                    
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("✅ Configuration Saved")
                        .setMessage("Porcupine is now configured with:\n\nWake Word: $selectedWakeWord\nSensitivity: ${String.format("%.1f", sensitivity)}\n\nRestart the app to use Porcupine wake word detection.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("❌ Invalid Configuration")
                        .setMessage("Please enter a valid Porcupine access key. You can get one free at https://console.picovoice.ai/")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNeutralButton("Disable") { _, _ ->
                preferencesManager.setPorcupineEnabled(false)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("⏸️ Porcupine Disabled")
                    .setMessage("Porcupine wake word detection has been disabled. The app will use traditional speech recognition instead.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========================================
    // PROTECTION MODE SERVICE INTEGRATION
    // ========================================
    
    /**
     * Initialize ProtectionModeService components
     */
    private fun initializeProtectionMode() {
        preferencesManager = PreferencesManager(requireContext())
        protectionModeManager = ProtectionModeManager.getInstance(requireContext())
    }
    
    /**
     * Start protection status monitoring
     */
    private fun startProtectionStatusMonitoring() {
        statusUpdateJob?.cancel()
        statusUpdateJob = lifecycleScope.launch {
            while (isActive) {
                updateProtectionModeStatus()
                delay(statusUpdateInterval)
            }
        }
    }
    
    /**
     * Stop protection status monitoring
     */
    private fun stopProtectionStatusMonitoring() {
        statusUpdateJob?.cancel()
        statusUpdateJob = null
    }
    
    /**
     * Update protection mode status display
     */
    private fun updateProtectionModeStatus() {
        try {
            val isActive = preferencesManager.isProtectionModeEnabled()
            val confirmationState = preferencesManager.loadConfirmationState()
            val emergencyCount = preferencesManager.getProtectionModeEmergencyCount()
            
            // Update background image based on protection status
            if (isActive) {
                binding.backgroundImage.setImageResource(R.drawable.home_clicked)
            } else {
                binding.backgroundImage.setImageResource(R.drawable.home_unclicked)
            }
            
            // Update hidden status text views
            binding.tvServiceStatus.text = if (isActive) "PROTECTION ACTIVE" else "PROTECTION INACTIVE"
            
            // Update listening status based on confirmation state
            when (confirmationState.count) {
                0 -> binding.tvListeningStatus.text = if (isActive) "LISTENING" else "STOPPED"
                1 -> binding.tvListeningStatus.text = "CONFIRMING (1/2)"
                else -> binding.tvListeningStatus.text = "EMERGENCY TRIGGERED"
            }
            
            // Update emergency count
            binding.tvLastActivity.text = "Emergencies: $emergencyCount/3"
            
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Error updating protection status", e)
        }
    }
    
    /**
     * Show comprehensive protection status dialog (replaces Porcupine config on long press)
     */
    private fun showProtectionStatusDialog() {
        val isActive = preferencesManager.isProtectionModeEnabled()
        val confirmationState = preferencesManager.loadConfirmationState()
        val emergencyCount = preferencesManager.getProtectionModeEmergencyCount()
        val wakePhrase = preferencesManager.getWakePhrase()
        val emergencyContact = preferencesManager.getBuddyPhone()
        
        val status = buildString {
            append("🛡️ PROTECTION MODE STATUS\n\n")
            
            append("Status: ${if (isActive) "🟢 ACTIVE" else "🔴 INACTIVE"}\n")
            append("Wake Phrase: ${if (wakePhrase.isNullOrEmpty()) "❌ Not Set" else "✅ Set"}\n")
            append("Emergency Contact: ${if (emergencyContact.isNullOrEmpty()) "❌ Not Set" else "✅ Set"}\n\n")
            
            append("🔍 DOUBLE CONFIRMATION\n")
            append("Count: ${confirmationState.count}/2\n")
            if (confirmationState.count > 0) {
                append("Keyword: ${confirmationState.keyword}\n")
                append("Detector: ${confirmationState.detectorType}\n")
                val ageMs = System.currentTimeMillis() - confirmationState.firstTime
                append("Age: ${ageMs / 1000}s\n")
                append("Valid: ${if (preferencesManager.isConfirmationStateValid(30000)) "✅" else "❌"}\n")
            }
            append("\n")
            
            append("🚨 EMERGENCY STATS\n")
            append("Today: $emergencyCount/3\n")
            append("Limit: 3 per day\n\n")
            
            append("⚙️ ML DETECTION\n")
            append("Mode: ${preferencesManager.getMLDetectionMode()}\n")
            append("Profile: ${preferencesManager.getMLPerformanceProfile()}\n")
        }
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Protection Status")
            .setMessage(status)
            .setPositiveButton(if (isActive) "Stop Protection" else "Start Protection") { _, _ ->
                toggleProtectionMode()
            }
            .setNeutralButton("Advanced Config") { _, _ ->
                showPorcupineConfigurationDialog()
            }
            .setNegativeButton("Close", null)
            
        // Add clear state option if there's confirmation state
        if (confirmationState.count > 0 || emergencyCount > 0) {
            dialog.setNeutralButton("Clear State") { _, _ ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Clear State")
                    .setMessage("Clear confirmation state and reset emergency count?")
                    .setPositiveButton("Clear") { _, _ ->
                        preferencesManager.clearConfirmationState()
                        preferencesManager.resetProtectionModeEmergencyCount()
                        android.widget.Toast.makeText(context, "State cleared", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        
        dialog.show()
    }
    
    /**
     * Toggle protection mode on/off
     */
    private fun toggleProtectionMode() {
        try {
            val isCurrentlyActive = preferencesManager.isProtectionModeEnabled()
            
            if (isCurrentlyActive) {
                showStopProtectionDialog()
            } else {
                showStartProtectionDialog()
            }
            
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Error toggling protection mode", e)
            android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Show dialog to confirm starting protection mode
     */
    private fun showStartProtectionDialog() {
        val wakePhrase = preferencesManager.getWakePhrase()
        
        if (wakePhrase.isNullOrEmpty()) {
            android.widget.Toast.makeText(context, "Please record your emergency phrase first", android.widget.Toast.LENGTH_LONG).show()
            findNavController().navigate(R.id.navigation_record)
            return
        }
        
        val emergencyContact = preferencesManager.getBuddyPhone()
        if (emergencyContact.isNullOrEmpty()) {
            android.widget.Toast.makeText(context, "Please set up emergency contact first", android.widget.Toast.LENGTH_LONG).show()
            findNavController().navigate(R.id.navigation_buddy)
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Activate Protection Mode")
            .setMessage("This will start silent monitoring for your emergency phrase.\n\n" +
                       "⚠️ Say your phrase TWICE within 30 seconds to trigger:\n" +
                       "• SMS alert to: ${emergencyContact.take(6)}***\n" +
                       "• Factory reset device\n\n" +
                       "Protection will run silently in background.")
            .setPositiveButton("Activate Protection") { _, _ ->
                startProtectionMode()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Show dialog to confirm stopping protection mode
     */
    private fun showStopProtectionDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Stop Protection Mode")
            .setMessage("This will stop monitoring for emergency phrases.\n\n" +
                       "Your device will no longer be protected.\n\n" +
                       "Are you sure you want to stop protection?")
            .setPositiveButton("Stop Protection") { _, _ ->
                stopProtectionMode()
            }
            .setNegativeButton("Keep Protected", null)
            .show()
    }
    
    /**
     * Start protection mode
     */
    private fun startProtectionMode() {
        try {
            val wakePhrase = preferencesManager.getWakePhrase() ?: ""
            protectionModeManager.enterProtectionMode(wakePhrase)
            
            android.widget.Toast.makeText(context, "🛡️ Protection Mode Activated", android.widget.Toast.LENGTH_SHORT).show()
            updateProtectionModeStatus()
            
            android.util.Log.i("HomeFragment", "Protection mode started from UI")
            
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Error starting protection mode", e)
            android.widget.Toast.makeText(context, "Failed to start protection: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Stop protection mode
     */
    private fun stopProtectionMode() {
        try {
            protectionModeManager.exitProtectionMode()
            
            android.widget.Toast.makeText(context, "🔴 Protection Mode Stopped", android.widget.Toast.LENGTH_SHORT).show()
            updateProtectionModeStatus()
            
            android.util.Log.i("HomeFragment", "Protection mode stopped from UI")
            
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Error stopping protection mode", e)
            android.widget.Toast.makeText(context, "Failed to stop protection: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopProtectionStatusMonitoring()
        _binding = null
    }
}
