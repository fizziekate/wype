package com.wype.security.ui.buddy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wype.security.R
import com.wype.security.databinding.FragmentBuddyBinding
import com.wype.security.utils.CountryCodeHelper

class BuddyFragment : Fragment() {

    companion object {
        private const val TAG = "BuddyFragment"
    }

    private var _binding: FragmentBuddyBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var buddyViewModel: BuddyViewModel
    
    // Contact picker launcher
    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { contactUri ->
                handleContactSelection(contactUri)
            }
        }
    }
    
    // SMS permission launcher
    private val requestSmsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            sendTestSms()
        } else {
            showPermissionDeniedDialog("SMS")
        }
    }
    
    // Contacts permission launcher
    private val requestContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openContactPicker()
        } else {
            showPermissionDeniedDialog("Contacts")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        buddyViewModel = ViewModelProvider(this)[BuddyViewModel::class.java]
        _binding = FragmentBuddyBinding.inflate(inflater, container, false)
        
        setupObservers()
        setupClickListeners()
        
        // Initialize with unclicked state
        showUnclickedState()
        
        return binding.root
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume() called - refreshing buddy info")
        // Refresh buddy info when returning to screen
        buddyViewModel.refreshBuddyInfo()
        
        // Debug: Log current state after refresh
        view?.post {
            Log.d(TAG, "After refresh - buddy: ${buddyViewModel.currentBuddy.value}, hasBuddy: ${buddyViewModel.hasBuddy.value}")
        }
    }
    
    private fun setupObservers() {
        Log.d(TAG, "Setting up observers...")
        
        buddyViewModel.currentBuddy.observe(viewLifecycleOwner) { buddy ->
            Log.d(TAG, "currentBuddy observer triggered with: $buddy")
            updateBuddyDisplay(buddy)
        }
        
        buddyViewModel.hasBuddy.observe(viewLifecycleOwner) { hasBuddy ->
            Log.d(TAG, "hasBuddy observer triggered with: $hasBuddy")
            updateUI(hasBuddy)
        }
        
        // Debug: Check initial values
        Log.d(TAG, "Initial buddy data: ${buddyViewModel.currentBuddy.value}")
        Log.d(TAG, "Initial hasBuddy: ${buddyViewModel.hasBuddy.value}")
    }
    
    private fun setupClickListeners() {
        binding.btnSelectContact.setOnClickListener {
            // Don't show button pressed animation for nominate - it will switch to clicked state permanently
            handleContactPickerRequest()
        }
        
        binding.btnTestSms.setOnClickListener {
            showButtonPressed()
            handleTestSmsRequest()
        }
        
        binding.btnRemoveBuddy.setOnClickListener {
            confirmRemoveBuddy()
        }
        
        binding.btnCallBuddy.setOnClickListener {
            callBuddy()
        }
        
        // Long press on test SMS button to configure SMS services
        binding.btnTestSms.setOnLongClickListener {
            showServiceConfigDialog()
            true
        }
    }
    
    private fun handleContactPickerRequest() {
        // Show options for contact selection
        showContactSelectionOptions()
    }
    
    private fun showContactSelectionOptions() {
        val current = buddyViewModel.currentBuddy.value
        val options = if (current != null) {
            arrayOf(
                "📱 Select from Contacts",
                "⌨️ Enter Manually", 
                "✏️ Edit Current (${current.name})"
            )
        } else {
            arrayOf(
                "📱 Select from Contacts",
                "⌨️ Enter Manually"
            )
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Buddy Contact")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Select from contacts
                        if (ContextCompat.checkSelfPermission(
                                requireContext(),
                                Manifest.permission.READ_CONTACTS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        } else {
                            openContactPicker()
                        }
                    }
                    1 -> {
                        // Enter manually
                        showPhoneInputDialog()
                    }
                    2 -> {
                        // Edit current (only available when there's a current contact)
                        current?.let {
                            showPhoneInputDialog(it.name, it.phone)
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showPhoneInputDialog(currentName: String = "", currentPhone: String = "") {
        PhoneInputDialog(
            requireContext(),
            currentName,
            currentPhone
        ) { name, phone ->
            // Validate the international phone number
            if (CountryCodeHelper.isValidInternationalPhone(phone)) {
                buddyViewModel.setBuddyContact(name, phone)
                showNominatedState()
                Toast.makeText(
                    requireContext(),
                    "Buddy contact saved: $name ($phone)",
                    Toast.LENGTH_SHORT
                ).show()
                // If phrase is also recorded, automatically prompt for permissions and start protection
                (requireActivity() as? com.wype.security.ui.MainActivity)?.triggerProtectionSetup()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Invalid international phone number format",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.show()
    }
    
    private fun openContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }
    
    private fun handleContactSelection(contactUri: Uri) {
        try {
            val cursor: Cursor? = requireContext().contentResolver.query(
                contactUri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    
                    if (nameIndex >= 0 && phoneIndex >= 0) {
                        val name = it.getString(nameIndex)
                        val phone = it.getString(phoneIndex)
                        
                        Log.d(TAG, "Selected contact: $name - $phone")
                        
                        // Clean and validate phone number
                        val cleanPhone = cleanPhoneNumber(phone)
                        if (isValidPhoneNumber(cleanPhone)) {
                            buddyViewModel.setBuddyContact(name, cleanPhone)

                            // Show the nominated/clicked state permanently
                            showNominatedState()

                            Toast.makeText(
                                requireContext(),
                                "Buddy contact saved: $name",
                                Toast.LENGTH_SHORT
                            ).show()
                            // If phrase is also recorded, automatically prompt for permissions and start protection
                            (requireActivity() as? com.wype.security.ui.MainActivity)?.triggerProtectionSetup()
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Invalid phone number format",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading contact", e)
            Toast.makeText(
                requireContext(),
                "Error reading contact information",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun handleTestSmsRequest() {
        if (!buddyViewModel.hasBuddy.value!!) {
            Toast.makeText(requireContext(), "Please select a buddy contact first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check SMS permission
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        } else {
            sendTestSms()
        }
    }
    
    private fun sendTestSms() {
        val buddy = buddyViewModel.currentBuddy.value
        if (buddy == null) {
            Toast.makeText(requireContext(), "No buddy contact selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val smsManager = SmsManager.getDefault()
            val testMessage = "WYPE Security Test: This is a test message from WYPE emergency app. Your buddy has configured you as their emergency contact."
            
            // Split long messages if needed
            val parts = smsManager.divideMessage(testMessage)
            if (parts.size == 1) {
                smsManager.sendTextMessage(buddy.phone, null, testMessage, null, null)
            } else {
                smsManager.sendMultipartTextMessage(buddy.phone, null, parts, null, null)
            }
            
            Log.i(TAG, "Test SMS sent to ${buddy.name} at ${buddy.phone}")
            Toast.makeText(
                requireContext(),
                "Test SMS sent to ${buddy.name}!",
                Toast.LENGTH_SHORT
            ).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending test SMS", e)
            Toast.makeText(
                requireContext(),
                "Failed to send test SMS: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun confirmRemoveBuddy() {
        val buddy = buddyViewModel.currentBuddy.value ?: return
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove Buddy Contact")
            .setMessage("Are you sure you want to remove ${buddy.name} as your emergency buddy?")
            .setPositiveButton("Remove") { _, _ ->
                buddyViewModel.removeBuddyContact()
                Toast.makeText(requireContext(), "Buddy contact removed", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun callBuddy() {
        val buddy = buddyViewModel.currentBuddy.value
        if (buddy == null) {
            Toast.makeText(requireContext(), "No buddy contact to call", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val callIntent = Intent(Intent.ACTION_DIAL)
            callIntent.data = Uri.parse("tel:${buddy.phone}")
            startActivity(callIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting call intent", e)
            Toast.makeText(requireContext(), "Error starting phone call", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showPermissionDeniedDialog(permissionType: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("$permissionType Permission Required")
            .setMessage("WYPE needs $permissionType permission to function properly. Please enable it in app settings.")
            .setPositiveButton("Settings") { _, _ ->
                // Open app settings
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${requireContext().packageName}")
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateBuddyDisplay(buddy: BuddyViewModel.BuddyContact?) {
        Log.d(TAG, "updateBuddyDisplay called with buddy: $buddy")
        
        if (buddy != null) {
            Log.d(TAG, "Buddy data: name='${buddy.name}', phone='${buddy.phone}'")
            
            // Update the existing hidden elements (for compatibility)
            binding.tvCurrentBuddy.text = buddy.name
            binding.tvBuddyPhone.text = buddy.phone
            binding.tvBuddyPhone.visibility = View.VISIBLE
            binding.layoutCurrentBuddy.visibility = View.VISIBLE
            
            // Update the new design text fields positioned over your background
            binding.tvBuddyNameField.text = buddy.name
            binding.tvBuddyPhoneField.text = buddy.phone
            binding.tvBuddyNameField.visibility = View.VISIBLE
            binding.tvBuddyPhoneField.visibility = View.VISIBLE
            
            // Show nominated state since buddy exists
            showNominatedState()
            
            Log.d(TAG, "Text fields updated: name field text='${binding.tvBuddyNameField.text}', phone field text='${binding.tvBuddyPhoneField.text}'")
            Log.d(TAG, "Text field visibilities: name=${binding.tvBuddyNameField.visibility}, phone=${binding.tvBuddyPhoneField.visibility}")
        } else {
            Log.d(TAG, "No buddy data - hiding text fields")
            
            // Hide existing elements
            binding.layoutCurrentBuddy.visibility = View.GONE
            
            // Clear and hide the design text fields
            binding.tvBuddyNameField.text = ""
            binding.tvBuddyPhoneField.text = ""
            binding.tvBuddyNameField.visibility = View.GONE
            binding.tvBuddyPhoneField.visibility = View.GONE
            
            // Revert to unclicked state since no buddy
            showUnclickedState()
        }
    }
    
    private fun updateUI(hasBuddy: Boolean) {
        binding.btnTestSms.isEnabled = hasBuddy
        binding.btnRemoveBuddy.isEnabled = hasBuddy
        binding.btnCallBuddy.isEnabled = hasBuddy
        
        if (hasBuddy) {
            binding.tvBuddyStatus.text = "Emergency buddy configured ✓"
            binding.tvBuddyStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.status_active)
            )
            binding.btnSelectContact.text = "Change Contact"
        } else {
            binding.tvBuddyStatus.text = "No emergency buddy selected"
            binding.tvBuddyStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.status_inactive)
            )
            binding.btnSelectContact.text = getString(R.string.buddy_select)
        }
    }
    
    private fun cleanPhoneNumber(phone: String): String {
        // Remove all non-digit characters except + at the beginning
        return phone.replace(Regex("[^+\\d]"), "")
    }
    
    private fun isValidPhoneNumber(phone: String): Boolean {
        // Basic validation - at least 10 digits
        val digitsOnly = phone.replace(Regex("[^\\d]"), "")
        return digitsOnly.length >= 10
    }
    
    private fun showButtonPressed() {
        // Show pressed state briefly for buttons other than nominate
        view?.findViewById<android.widget.ImageView>(R.id.buddy_background)?.let { imageView ->
            imageView.setImageResource(R.drawable.nominate_buddy_unclicked)
            
            // Return to original state after delay
            imageView.postDelayed({
                imageView.setImageResource(R.drawable.nominate_buddy_clicked)
            }, 150) // 150ms button press feedback
        }
    }
    
    private fun showNominatedState() {
        // Show the nominated state permanently (buddy selected)
        view?.findViewById<android.widget.ImageView>(R.id.buddy_background)?.let { imageView ->
            imageView.setImageResource(R.drawable.nominate_buddy_unclicked)
        }
    }
    
    private fun showUnclickedState() {
        // Show the unclicked state when no buddy is nominated
        view?.findViewById<android.widget.ImageView>(R.id.buddy_background)?.let { imageView ->
            imageView.setImageResource(R.drawable.nominate_buddy_clicked)
        }
    }
    
    private fun showServiceConfigDialog() {
        val options = arrayOf(
            "📧 Twilio SMS Configuration",
            "🎤 Azure Speech Configuration",
            "⚙️ Speech Recognition Mode"
        )
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Service Configuration")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTwilioConfigDialog()
                    1 -> showAzureSpeechConfigDialog() 
                    2 -> showSpeechModeDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showTwilioConfigDialog() {
        val prefsManager = com.wype.security.utils.PreferencesManager(requireContext())
        
        // Create input layout
        val inputLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }
        
        val accountSidInput = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = "Account SID"
            setText(prefsManager.getTwilioAccountSid() ?: "")
        }
        
        val authTokenInput = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = "Auth Token"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefsManager.getTwilioAuthToken() ?: "")
        }
        
        val fromPhoneInput = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = "From Phone Number (e.g. +1234567890)"
            setText(prefsManager.getTwilioFromPhone() ?: "")
        }
        
        val enabledCheckbox = android.widget.CheckBox(requireContext()).apply {
            text = "Enable Twilio SMS (silent sending)"
            isChecked = prefsManager.isTwilioEnabled()
        }
        
        inputLayout.addView(android.widget.TextView(requireContext()).apply {
            text = "Twilio SMS Configuration"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 30)
        })
        
        inputLayout.addView(android.widget.TextView(requireContext()).apply {
            text = "Get your credentials from Twilio Console"
            textSize = 12f
            setPadding(0, 0, 0, 20)
        })
        
        inputLayout.addView(accountSidInput)
        inputLayout.addView(authTokenInput)
        inputLayout.addView(fromPhoneInput)
        inputLayout.addView(enabledCheckbox)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("SMS Provider Settings")
            .setView(inputLayout)
            .setPositiveButton("Save") { _, _ ->
                val accountSid = accountSidInput.text.toString().trim()
                val authToken = authTokenInput.text.toString().trim()
                val fromPhone = fromPhoneInput.text.toString().trim()
                
                if (accountSid.isNotEmpty() && authToken.isNotEmpty() && fromPhone.isNotEmpty()) {
                    prefsManager.setTwilioCredentials(accountSid, authToken, fromPhone)
                    prefsManager.setTwilioEnabled(enabledCheckbox.isChecked)
                    
                    Toast.makeText(
                        requireContext(),
                        "Twilio SMS configured ${if (enabledCheckbox.isChecked) "and enabled" else "but disabled"}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (enabledCheckbox.isChecked) {
                    Toast.makeText(
                        requireContext(),
                        "Please fill in all Twilio credentials",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    prefsManager.setTwilioEnabled(false)
                    Toast.makeText(requireContext(), "Twilio SMS disabled", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear") { _, _ ->
                prefsManager.clearTwilioCredentials()
                Toast.makeText(requireContext(), "Twilio credentials cleared", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun showAzureSpeechConfigDialog() {
        val prefsManager = com.wype.security.utils.PreferencesManager(requireContext())
        
        // Create input layout
        val inputLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }
        
        val speechKeyInput = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = "Speech Service Key"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefsManager.getAzureSpeechKey() ?: "")
        }
        
        val regionInput = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = "Region (e.g. eastus, westus2)"
            setText(prefsManager.getAzureSpeechRegion() ?: "")
        }
        
        val enabledCheckbox = android.widget.CheckBox(requireContext()).apply {
            text = "Enable Azure Speech (cloud recognition)"
            isChecked = prefsManager.isAzureSpeechEnabled()
        }
        
        inputLayout.addView(android.widget.TextView(requireContext()).apply {
            text = "Azure Speech Configuration"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 30)
        })
        
        inputLayout.addView(android.widget.TextView(requireContext()).apply {
            text = "Get your credentials from Azure Portal"
            textSize = 12f
            setPadding(0, 0, 0, 20)
        })
        
        inputLayout.addView(speechKeyInput)
        inputLayout.addView(regionInput)
        inputLayout.addView(enabledCheckbox)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Azure Speech Settings")
            .setView(inputLayout)
            .setPositiveButton("Save") { _, _ ->
                val speechKey = speechKeyInput.text.toString().trim()
                val region = regionInput.text.toString().trim()
                
                if (speechKey.isNotEmpty() && region.isNotEmpty()) {
                    prefsManager.setAzureSpeechCredentials(speechKey, region)
                    prefsManager.setAzureSpeechEnabled(enabledCheckbox.isChecked)
                    
                    Toast.makeText(
                        requireContext(),
                        "Azure Speech configured ${if (enabledCheckbox.isChecked) "and enabled" else "but disabled"}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (enabledCheckbox.isChecked) {
                    Toast.makeText(
                        requireContext(),
                        "Please fill in all Azure Speech credentials",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    prefsManager.setAzureSpeechEnabled(false)
                    Toast.makeText(requireContext(), "Azure Speech disabled", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear") { _, _ ->
                prefsManager.clearAzureSpeechCredentials()
                Toast.makeText(requireContext(), "Azure Speech credentials cleared", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun showSpeechModeDialog() {
        val prefsManager = com.wype.security.utils.PreferencesManager(requireContext())
        val currentMode = prefsManager.getSpeechRecognitionMode()
        
        val modes = arrayOf(
            "Auto (Try Azure first, fallback to local)",
            "Azure Cognitive Services Only",
            "Local Android Speech Only"
        )
        
        val selectedIndex = when (currentMode) {
            com.wype.security.utils.PreferencesManager.SpeechRecognitionMode.AUTO -> 0
            com.wype.security.utils.PreferencesManager.SpeechRecognitionMode.AZURE_COGNITIVE -> 1
            com.wype.security.utils.PreferencesManager.SpeechRecognitionMode.LOCAL_ANDROID -> 2
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Speech Recognition Mode")
            .setSingleChoiceItems(modes, selectedIndex) { dialog, which ->
                val newMode = when (which) {
                    0 -> com.wype.security.utils.PreferencesManager.SpeechRecognitionMode.AUTO
                    1 -> com.wype.security.utils.PreferencesManager.SpeechRecognitionMode.AZURE_COGNITIVE
                    2 -> com.wype.security.utils.PreferencesManager.SpeechRecognitionMode.LOCAL_ANDROID
                    else -> com.wype.security.utils.PreferencesManager.SpeechRecognitionMode.AUTO
                }
                
                prefsManager.setSpeechRecognitionMode(newMode)
                
                Toast.makeText(
                    requireContext(),
                    "Speech recognition mode set to: ${modes[which]}",
                    Toast.LENGTH_LONG
                ).show()
                
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
