package com.wype.security.provisioning

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Minimal DeviceAdminReceiver to receive enable/disable callbacks.
 * Keep this lightweight; most policy actions are in PolicyManager.
 */
class DeviceAdminRcvr : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Wype device admin enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Wype device admin disabled", Toast.LENGTH_SHORT).show()
    }
}