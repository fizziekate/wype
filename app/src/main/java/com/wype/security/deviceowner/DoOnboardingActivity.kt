package com.wype.security.deviceowner

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.wype.security.R

/**
 * Non-destructive onboarding shell for wiring routes:
 * - Register -> Home -> Backup -> Enable DO
 * TODO: Replace with real UI and navigation; keep DO actions behind flags.
 */
class DoOnboardingActivity : AppCompatActivity() {
    private val navigator = DoOnboardingNavigator()
    private val backup = DoBackupCoordinator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_do_onboarding)
    }
}
