package com.wype.security.deviceowner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Non-destructive onboarding shell for wiring routes:
 * - Register -> Home -> Backup -> Enable DO
 * TODO: Replace with real UI and navigation; keep DO actions behind flags.
 */
class DoOnboardingActivity : ComponentActivity() {
    private val navigator = DoOnboardingNavigator()
    private val backup = DoBackupCoordinator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // TODO: Compose or Views to render screens using PNGs:
            // - do_register.png
            // - do_home_pressed.png
            // - do_google_backup_unclicked.png / do_google_backup_clicked.png
            // NOTE: No DevicePolicyManager calls in this stub.
        }
    }
}
