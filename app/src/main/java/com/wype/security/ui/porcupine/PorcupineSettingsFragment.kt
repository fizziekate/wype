package com.wype.security.ui.porcupine

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.wype.security.R
import com.wype.security.databinding.FragmentPorcupineSettingsBinding
import com.wype.security.services.PorcupineService
import com.wype.security.utils.PreferencesManager
import java.text.DecimalFormat

class PorcupineSettingsFragment : Fragment() {

    private var _binding: FragmentPorcupineSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var preferencesManager: PreferencesManager
    private val decimalFormat = DecimalFormat("0.0")
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPorcupineSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        preferencesManager = PreferencesManager(requireContext())
        
        setupWakeWordSpinner()
        setupSensitivitySeekBar()
        setupButtons()
        loadCurrentSettings()
        updateStatus()
    }

    private fun setupWakeWordSpinner() {
        val wakeWords = resources.getStringArray(R.array.wake_words_display)
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            wakeWords
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        
        binding.spinnerWakeWord.adapter = adapter
        
        binding.spinnerWakeWord.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateStatus()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSensitivitySeekBar() {
        binding.seekBarSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sensitivity = progress / 100.0f
                binding.textViewSensitivityValue.text = decimalFormat.format(sensitivity)
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupButtons() {
        binding.buttonSaveSettings.setOnClickListener {
            saveSettings()
        }
        
        binding.buttonTestWakeWord.setOnClickListener {
            testWakeWord()
        }
    }

    private fun loadCurrentSettings() {
        // Load Access Key
        val accessKey = preferencesManager.getPorcupineAccessKey() ?: ""
        binding.editTextAccessKey.setText(accessKey)
        
        // Load Wake Word
        val currentWakeWord = preferencesManager.getPorcupineWakeWord() ?: "picovoice"
        val wakeWordValues = resources.getStringArray(R.array.wake_words)
        val position = wakeWordValues.indexOf(currentWakeWord.lowercase())
        if (position >= 0) {
            binding.spinnerWakeWord.setSelection(position)
        }
        
        // Load Sensitivity
        val sensitivity = preferencesManager.getPorcupineSensitivity()
        val progress = (sensitivity * 100).toInt()
        binding.seekBarSensitivity.progress = progress
        binding.textViewSensitivityValue.text = decimalFormat.format(sensitivity)
    }

    private fun saveSettings() {
        val accessKey = binding.editTextAccessKey.text.toString().trim()
        
        if (accessKey.isEmpty()) {
            Toast.makeText(context, "Please enter a valid Access Key", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get selected wake word
        val selectedIndex = binding.spinnerWakeWord.selectedItemPosition
        val wakeWordValues = resources.getStringArray(R.array.wake_words)
        val wakeWord = if (selectedIndex >= 0 && selectedIndex < wakeWordValues.size) {
            wakeWordValues[selectedIndex]
        } else {
            "picovoice"
        }
        
        // Get sensitivity
        val sensitivity = binding.seekBarSensitivity.progress / 100.0f
        
        // Save to preferences
        preferencesManager.setPorcupineAccessKey(accessKey)
        preferencesManager.setPorcupineWakeWord(wakeWord)
        preferencesManager.setPorcupineSensitivity(sensitivity)
        preferencesManager.setPorcupineEnabled(true)
        
        updateStatus()
        
        Toast.makeText(context, "Settings saved successfully", Toast.LENGTH_SHORT).show()
        
        // Restart Porcupine service with new settings
        restartPorcupineService()
    }

    private fun testWakeWord() {
        if (!preferencesManager.isPorcupineConfigured()) {
            Toast.makeText(context, "Please save settings first", Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(
            context, 
            "Say \"${getSelectedWakeWordDisplay()}\" to test detection", 
            Toast.LENGTH_LONG
        ).show()
        
        // Start Porcupine service for testing
        startPorcupineService()
    }

    private fun getSelectedWakeWordDisplay(): String {
        val selectedIndex = binding.spinnerWakeWord.selectedItemPosition
        val wakeWordDisplayValues = resources.getStringArray(R.array.wake_words_display)
        return if (selectedIndex >= 0 && selectedIndex < wakeWordDisplayValues.size) {
            wakeWordDisplayValues[selectedIndex]
        } else {
            "Picovoice"
        }
    }

    private fun updateStatus() {
        val isConfigured = preferencesManager.isPorcupineConfigured() && 
                          binding.editTextAccessKey.text.toString().trim().isNotEmpty()
        
        val statusText = when {
            !isConfigured -> getString(R.string.porcupine_not_configured)
            preferencesManager.isPorcupineEnabled() -> getString(R.string.porcupine_configured)
            else -> getString(R.string.porcupine_disabled)
        }
        
        binding.textViewStatus.text = statusText
    }

    private fun startPorcupineService() {
        val intent = Intent(context, PorcupineService::class.java).apply {
            action = PorcupineService.ACTION_START_SERVICE
        }
        context?.startService(intent)
    }

    private fun restartPorcupineService() {
        val intent = Intent(context, PorcupineService::class.java).apply {
            action = PorcupineService.ACTION_RESTART_SERVICE
        }
        context?.startService(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
