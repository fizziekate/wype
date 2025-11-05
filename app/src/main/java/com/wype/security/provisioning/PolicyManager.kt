package com.wype.security.provisioning

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/**
 * Helper wrapper around DevicePolicyManager for the app.
 * Methods guard on admin/owner state as requested.
 */
class PolicyManager(private val context: Context) {
    private val dpm: DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)
            ?: throw IllegalStateException("DevicePolicyManager unavailable")

    private val adminComponent = ComponentName(context, DeviceAdminRcvr::class.java)
    private val appPackage = context.packageName

    /**
     * True if Device Admin (active) for the admin receiver component.
     */
    fun isAdminActive(): Boolean = dpm.isAdminActive(adminComponent)

    /**
     * True if app is device owner (device owner app).
     */
    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(appPackage)

    /**
     * Lock the device immediately. Requires the app to be admin or device owner.
     * Throws IllegalStateException if not allowed.
     */
    fun lockNow() {
        if (isAdminActive() || isDeviceOwner()) {
            dpm.lockNow()
        } else {
            throw IllegalStateException("Cannot lock device: app is not device admin or owner")
        }
    }

    /**
     * Wipe the device (factory reset). Only allowed for device owner.
     * Throws IllegalStateException if not device owner.
     */
    fun wipeNow() {
        if (isDeviceOwner()) {
            // flags = 0 for full wipe. Adjust flags if needed in future.
            dpm.wipeData(0)
        } else {
            throw IllegalStateException("Cannot wipe device: app is not device owner")
        }
    }
}