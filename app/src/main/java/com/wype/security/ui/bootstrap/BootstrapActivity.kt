package com.wype.security.ui.bootstrap

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.material.snackbar.Snackbar
import com.wype.security.databinding.ActivityBootstrapBinding
import com.wype.security.work.RestoreBootstrapWorker

class BootstrapActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBootstrapBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBootstrapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Prefill from deep link: wype://bootstrap/<code>
        val data: Uri? = intent?.data
        val deeplinkCode = data?.lastPathSegment ?: intent?.getStringExtra("code")
        if (!deeplinkCode.isNullOrBlank()) {
            binding.etCode.setText(deeplinkCode)
        }

        binding.btnPaste.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
            if (!text.isNullOrBlank()) {
                binding.etCode.setText(text)
            } else {
                Toast.makeText(this, getString(com.wype.security.R.string.bootstrap_paste_empty), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRestore.setOnClickListener {
            val code = binding.etCode.text?.toString()?.trim()?.uppercase()
            if (code.isNullOrBlank() || code.length < 6) {
                binding.tilCode.error = getString(com.wype.security.R.string.bootstrap_code_error)
                return@setOnClickListener
            }
            binding.tilCode.error = null
            setUiLoading(true)
            enqueueRestore(code)
        }
    }

    private fun enqueueRestore(code: String) {
        val req = OneTimeWorkRequestBuilder<RestoreBootstrapWorker>()
            .setInputData(workDataOf("code" to code))
            .build()
        val wm = WorkManager.getInstance(this)
        wm.enqueue(req)

        binding.tvProgress.text = getString(com.wype.security.R.string.bootstrap_restoring)
        wm.getWorkInfoByIdLiveData(req.id).observe(this) { info ->
            if (info != null) {
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING -> {
                        val status = info.progress.getString("status")
                        if (!status.isNullOrBlank()) binding.tvProgress.text = status
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        Snackbar.make(binding.root, getString(com.wype.security.R.string.bootstrap_restore_success), Snackbar.LENGTH_LONG).show()
                        finish()
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        setUiLoading(false)
                        Snackbar.make(binding.root, getString(com.wype.security.R.string.bootstrap_restore_failed), Snackbar.LENGTH_LONG).show()
                    }
                    else -> { /* BLOCKED: ignore */ }
                }
            }
        }
    }

    private fun setUiLoading(loading: Boolean) {
        binding.btnRestore.isEnabled = !loading
        binding.btnPaste.isEnabled = !loading
        binding.etCode.isEnabled = !loading
        binding.progressBar.isVisible = loading
        binding.tvProgress.isVisible = loading
    }
}
