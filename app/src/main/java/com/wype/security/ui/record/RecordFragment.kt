package com.wype.security.ui.record

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wype.security.R
import com.wype.security.databinding.FragmentRecordBinding
import java.io.File
import java.io.IOException

class RecordFragment : Fragment() {

    companion object {
        private const val TAG = "RecordFragment"
    }

    private var _binding: FragmentRecordBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var recordViewModel: RecordViewModel
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordingFile: File? = null
    private var isRecording = false
    private var isPlaying = false
    private var isStopClicked = false
    
    private val handler = Handler(Looper.getMainLooper())
    private var recordingTimeRunnable: Runnable? = null
    private var recordingStartTime = 0L
    
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startRecording()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        recordViewModel = ViewModelProvider(this)[RecordViewModel::class.java]
        _binding = FragmentRecordBinding.inflate(inflater, container, false)
        
        setupObservers()
        setupClickListeners()
        updateUI()
        
        return binding.root
    }
    
    private fun setupObservers() {
        recordViewModel.currentPhrase.observe(viewLifecycleOwner) { phrase ->
            updateCurrentPhraseDisplay(phrase)
        }
        
        recordViewModel.hasAudioFile.observe(viewLifecycleOwner) { hasAudio ->
            updateButtonStates(hasAudio)
        }
    }
    
    private fun setupClickListeners() {
        binding.btnStartRecording.setOnClickListener {
            // Record button state is managed by recording state, no temporary flash needed
            if (isRecording) {
                stopRecording()
            } else {
                // Clear any stop clicked state when starting to record
                handleRecordingRequest()
            }
        }
        
        binding.btnPlayRecording.setOnClickListener {
            Log.d(TAG, "Play button clicked - isPlaying: $isPlaying, isRecording: $isRecording")
            
            val hasAudio = recordViewModel.hasAudioFile.value ?: false
            val audioPath = recordViewModel.getAudioFilePath()
            Log.d(TAG, "Audio available: $hasAudio, path: $audioPath")
            
            if (!hasAudio || audioPath.isNullOrEmpty()) {
                return@setOnClickListener
            }
            
            // Allow playing if we're not currently playing (regardless of recording state)
            if (!isPlaying) {
                playRecording()
            }
        }
        
        binding.btnStopPlayback.setOnClickListener {
            Log.d(TAG, "Stop button clicked - isPlaying: $isPlaying, isRecording: $isRecording")
            
            // Stop playback if active
            if (isPlaying) {
                stopPlayback()
            }
            
            // Stop recording if active
            if (isRecording) {
                stopRecording()
            }
            
            // Force reset states to ensure clean UI
            isPlaying = false
            
            // Always stop waveform animation
            binding.waveformView.setRecording(false)
            
            // Set stop clicked flag and show stop visual feedback
            isStopClicked = true
            binding.ivRecordBackground.setImageResource(R.drawable.not_recording_stop_clicked)
            
            // Update button states immediately
            updateButtonStates(recordViewModel.hasAudioFile.value ?: false)
        }
        
        // Save and delete buttons are now hidden - functionality moved to post-recording dialog
    }
    
    private fun handleRecordingRequest() {
        // Check if microphone permission is granted
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startRecording()
        }
    }
    
    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.permission_microphone_title))
            .setMessage(getString(R.string.permission_microphone_message))
            .setPositiveButton(getString(R.string.ok)) { _, _ -> }
            .show()
    }
    
    private fun startRecording() {
        try {
            // Use .m4a (AAC in MPEG-4) at 16 kHz so the template sample rate matches
            // the 16 kHz live audio captured by TemplateWakeWordDetector.
            recordingFile = File(requireContext().filesDir, "wake_phrase_recording.m4a")

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(recordingFile?.absolutePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioChannels(1)
                setAudioEncodingBitRate(64000)

                prepare()
                start()
            }
            
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            updateRecordingUI(true)
            startRecordingTimer()
            
            Log.d(TAG, "Recording started")
            
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start recording", e)
            // Toast removed to keep UI clean
        }
    }
    
    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            isRecording = false
            updateRecordingUI(false)
            stopRecordingTimer()
            
            // Save audio file path and enable playback
            recordingFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    Log.d(TAG, "Recording saved: ${file.absolutePath}")
                    // Automatically save the recording without dialog
                    recordViewModel.saveWakePhrase("Recorded wake phrase")
                    recordViewModel.setAudioFile(file.absolutePath)
                } else {
                    Log.w(TAG, "Recording file is empty or doesn't exist")
                    // Toast removed to keep UI clean
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            // Toast removed to keep UI clean
        }
    }
    
    private fun playRecording() {
        val audioPath = recordViewModel.getAudioFilePath()
        if (audioPath.isNullOrEmpty()) {
            // Toast removed to keep UI clean - just return silently
            return
        }
        
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioPath)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                }
                start()
            }
            
            isPlaying = true
            updatePlaybackUI(true)
            
            Log.d(TAG, "Playback started")
            
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start playback", e)
            // Toast removed to keep UI clean
        }
    }
    
    private fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        
        isPlaying = false
        updatePlaybackUI(false)
        
        Log.d(TAG, "Playback stopped")
    }
    
    // Old save/delete methods removed - functionality moved to post-recording dialog
    
    private fun startRecordingTimer() {
        recordingTimeRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    // Keep timer running but don't update any UI text elements
                    // Timer is kept for internal logic but no visible updates
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(recordingTimeRunnable!!)
    }
    
    private fun stopRecordingTimer() {
        recordingTimeRunnable?.let { handler.removeCallbacks(it) }
        // Don't update any text elements
    }
    
    private fun updateRecordingUI(recording: Boolean) {
        // Don't update any status text - keep all text elements hidden
        // Just update the background image and waveform
        if (recording) {
            // Clear stop clicked state when starting recording
            isStopClicked = false
            // Show clicked state during recording
            binding.ivRecordBackground.setImageResource(R.drawable.recording_clicked)
        } else {
            // Only update background if stop is not clicked
            if (!isStopClicked) {
                // Show unclicked state when not recording
                binding.ivRecordBackground.setImageResource(R.drawable.recording_unclicked)
            }
        }
        
        // Update waveform
        binding.waveformView.setRecording(recording)
        
        // Update all button states
        updateButtonStates(recordViewModel.hasAudioFile.value ?: false)
    }
    
    private fun updateButtonStates(hasAudio: Boolean) {
        // Record button: Always enabled (can start recording or stop current recording)
        binding.btnStartRecording.isEnabled = true
        
        // Play button: Enable if we have audio and not currently playing
        binding.btnPlayRecording.isEnabled = hasAudio && !isPlaying
        
        // Stop button: Always enabled - can stop any active operation
        binding.btnStopPlayback.isEnabled = true
        
        Log.d(TAG, "Button states updated - hasAudio: $hasAudio, isRecording: $isRecording, isPlaying: $isPlaying")
        Log.d(TAG, "Record button enabled: ${binding.btnStartRecording.isEnabled}, Play button enabled: ${binding.btnPlayRecording.isEnabled}, Stop button enabled: ${binding.btnStopPlayback.isEnabled}")
    }
    
    private fun updatePlaybackUI(playing: Boolean) {
        if (playing) {
            // Clear stop clicked state when starting playback
            isStopClicked = false
            // Show play state using the specific play drawable
            binding.ivRecordBackground.setImageResource(R.drawable.not_recording_play_clicked)
            
            // Start waveform animation during playback
            binding.waveformView.setRecording(true)
        } else {
            // Only revert to normal background if stop is not clicked and not recording
            if (!isRecording && !isStopClicked) {
                binding.ivRecordBackground.setImageResource(R.drawable.recording_unclicked)
            }
            
            // Stop waveform animation when not playing (unless we're recording)
            if (!isRecording) {
                binding.waveformView.setRecording(false)
            }
        }
        
        // Update play/stop button visibility and states
        updatePlayStopButtonStates()
        
        // Update all button states
        updateButtonStates(recordViewModel.hasAudioFile.value ?: false)
    }
    
    private fun updatePlayStopButtonStates() {
        // Visual feedback is now handled through the main recording background
        // The play and stop buttons are invisible overlays over your design
        // All visual state changes are managed through the main background image
    }
    
    private fun updateCurrentPhraseDisplay(phrase: String?) {
        // Keep all text elements hidden to preserve custom design
        if (!phrase.isNullOrEmpty()) {
            binding.etPhraseText.setText(phrase)
        }
        // Always keep text overlay hidden
        binding.tvCurrentPhrase.visibility = View.GONE
    }
    
    private fun updateUI() {
        updateRecordingUI(false)
        updatePlaybackUI(false)
        // Initialize play/stop button states
        updatePlayStopButtonStates()
    }
    
    
    private fun showButtonPressed() {
        // Only show temporary feedback for non-record buttons (play, save, delete)
        // Record button background state is managed by recording state
        if (!isRecording) {
            // Show clicked version temporarily
            binding.ivRecordBackground.setImageResource(R.drawable.recording_clicked)
            
            // Revert to unclicked after 150ms
            Handler(Looper.getMainLooper()).postDelayed({
                binding.ivRecordBackground.setImageResource(R.drawable.recording_unclicked)
            }, 150)
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Stop any ongoing recording or playback when leaving the screen
        if (isRecording) {
            stopRecording()
        }
        if (isPlaying) {
            stopPlayback()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        // Clean up media resources
        if (isRecording) {
            stopRecording()
        }
        if (isPlaying) {
            stopPlayback()
        }
        
        handler.removeCallbacks(recordingTimeRunnable ?: return)
        _binding = null
    }
}
