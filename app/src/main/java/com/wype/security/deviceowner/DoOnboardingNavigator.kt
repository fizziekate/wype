package com.wype.security.deviceowner

sealed class DoRoute {
    data object Register : DoRoute()
    data object Home : DoRoute()
    data object GoogleBackup : DoRoute()
    data object GoogleBackupConfirmed : DoRoute()
    data object EnableDeviceOwner : DoRoute()
}

class DoOnboardingNavigator {
    var current: DoRoute = DoRoute.Register
        private set

    fun onRegisterSubmit(success: Boolean) {
        if (success) current = DoRoute.Home
    }

    fun onTapBackup() { current = DoRoute.GoogleBackup }
    fun onBackupConfirm() { current = DoRoute.GoogleBackupConfirmed }
    fun onBack() { current = DoRoute.Home }
    fun onTapEnableDO() { current = DoRoute.EnableDeviceOwner }
}
