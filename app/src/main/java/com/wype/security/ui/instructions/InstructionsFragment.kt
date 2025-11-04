package com.wype.security.ui.instructions

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wype.security.R
import com.wype.security.databinding.FragmentInstructionsBinding
import com.wype.security.utils.PermissionManager

class InstructionsFragment : Fragment() {

    private var _binding: FragmentInstructionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var permissionManager: PermissionManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstructionsBinding.inflate(inflater, container, false)
        permissionManager = PermissionManager(requireActivity())
        setupEnableDeviceAdminButton()
        return binding.root
    }
    
    private fun setupEnableDeviceAdminButton() {
        binding.btnLogout.setOnClickListener {
            Log.d("InstructionsFragment", "Enable Device Admin button clicked")
            showEnableDeviceAdminDialog()
        }
        
        // Optional: long-press opens Device Admin settings directly
        binding.btnLogout.setOnLongClickListener {
            try {
                permissionManager.openDeviceAdminSettings()
                true
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Unable to open device admin settings", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    private fun showEnableDeviceAdminDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Enable Device Admin")
            .setMessage(
                "Wype needs Device Admin to lock or wipe your device during emergencies. " +
                "This does not give Wype full control; it only enables specific security actions."
            )
            .setPositiveButton("Enable Now") { _, _ ->
                permissionManager.requestDeviceAdminPermission()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Open Settings") { _, _ ->
                permissionManager.openDeviceAdminSettings()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
