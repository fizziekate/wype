package com.wype.security.deviceowner

class DoBackupCoordinator {
    enum class State { Idle, GoogleBackupPrompt, GoogleBackupConfirm }

    var state: State = State.Idle
        private set

    fun onTapBackup() { state = State.GoogleBackupPrompt }
    fun onBackupConfirm() { state = State.GoogleBackupConfirm }
    fun onBackToHome() { state = State.Idle }
}
