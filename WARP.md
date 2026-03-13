# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

Project overview
- Monorepo with:
  - Android app (module `:app`) that listens for a wake phrase and triggers an emergency pipeline (SMS → optional Google Drive backup → factory reset).
  - Minimal TypeScript/Express backend under `backend/` supporting provisioning and email flows.
- Toolchain: Gradle (wrapper included), Kotlin/Android (AGP 8.5.2, Java 17), TypeScript (tsc/tsx).

Common terminal commands
- Android (run from repo root):
  - Build all: `./gradlew build` (Windows: `.\\gradlew build`)
  - Assemble debug APK: `./gradlew :app:assembleDebug`
  - Install debug APK to device/emulator: `./gradlew :app:installDebug`
  - Lint: `./gradlew :app:lint`
  - Unit tests: `./gradlew :app:testDebugUnitTest`
  - Instrumented tests (device/emulator required): `./gradlew :app:connectedDebugAndroidTest`
  - Run a single unit test (example): `./gradlew :app:testDebugUnitTest --tests "com.wype.security.SomeTestClass"`
  - Run a single instrumentation test class (example):
    `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wype.security.hotword.HotwordEngineInstrumentationTest`
- Backend (from `backend/`):
  - Install deps: `npm install`
  - Dev (watch): `npm run dev`
  - Build: `npm run build`
  - Start: `npm start` (serves compiled `dist/index.js`, default port 8080)

Device Owner (DO) provisioning QR (PowerShell)
- Edit `scripts/provisioning.json` with your provisioning payload.
- Prepare QR command: `pwsh -File .\scripts\generate_wype_do_qr.ps1 -JsonPath .\scripts\provisioning.json -OutputPath .\templates\assets\wype_do_qr.png`
  - The script validates/compacts JSON and prints an example `npx qrcode` command; execute the printed command to actually generate the PNG (Node.js required).
- Email templates: `templates/do_provisioning_email.html|.txt` (embed the PNG via CID `wype-do-qr`).

High-level architecture
- Application bootstrap
  - `app/src/main/java/com/wype/security/WypeApp.kt`: initializes Firebase and creates ultra‑silent notification channels used by background services.
- Primary detection path
  - `accessibility/WypeAccessibilityService.kt`: foreground accessibility service that owns wake-word detection and emergency triggering. Uses double‑detection within 30s, debouncing, and per‑day limits; coordinates with `ProtectionModeManager`.
  - `ml/HybridWakeWordManager.kt`: selects between detectors (Porcupine, TensorFlow Lite, Simple neural) and reports detections with confidence.
- Background protection service
  - `service/ProtectionModeService.kt`: alternative continuous detection service with the same double‑confirmation logic and persistence of confirmation state.
- Legacy detection (kept for reference, avoid for new work)
  - `service/SpeechListenerService.kt` and `service/HotwordService.kt`: older audio paths with efforts to suppress beeps; superseded by the accessibility + hybrid ML path.
- Emergency pipeline
  - `service/EmergencySmsService.kt`: composes/sends SMS (supports Twilio via `TwilioSmsService` or local `SmsManager`), duplicate/cool‑down prevention, optional location.
  - `service/GoogleBackupService.kt`: optional Google Drive text backup of configuration/log before wipe; on success/failure proceeds to reset.
  - `service/FactoryResetService.kt` + `admin/WypeDeviceAdminReceiver.kt`: validates conditions then invokes `DevicePolicyManager.wipeData(...)` when admin is active.
- System integration and resilience
  - `receiver/BootReceiver.kt` (+ a hotword boot receiver): restarts services after boot/update if configured.
  - `service/ServiceWatchdog.kt`: AlarmManager‑based monitor to ensure critical services stay running.
- UI and configuration
  - `ui/MainActivity.kt` with 5 sections (Home, Instructions, Record/Play, Buddy, Backup) and view models.
  - `utils/PreferencesManager.kt`: single source of truth for wake phrase, buddy contact, backup/admin flags, detection logs, Twilio/Azure toggles, protection‑mode counters, and confirmation state.
- Device Owner onboarding helpers
  - `deviceowner/` package (e.g., `DoProvisioningSpec.kt`, `DoOnboardingActivity.kt`, `DoEmailService.kt`) plus PowerShell scripts and email templates under `scripts/` and `templates/`.
- Backend (provisioning/email stubs)
  - `backend/src/index.ts`: Express server with endpoints for `/register`, `/send-do-email`, `/otp/start`, `/otp/verify` (scaffolded with zod validation).

Important specifics from README
- SDK: compileSdk 34, targetSdk 34, minSdk 24; Kotlin; Gradle Kotlin DSL.
- Build status noted as fully successful with AGP 8.5.2 and Java 17; `gradlew build` passes; lint baseline present.
- App triggers sensitive flows (SMS, admin wipe). Test on emulators and disposable devices; exercise extreme caution with factory reset features.

Key file paths and references
- Gradle: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`
- Android manifest: `app/src/main/AndroidManifest.xml`
- Application: `app/src/main/java/com/wype/security/WypeApp.kt`
- Detection: `app/src/main/java/com/wype/security/accessibility/WypeAccessibilityService.kt`, `app/src/main/java/com/wype/security/ml/HybridWakeWordManager.kt`, `app/src/main/java/com/wype/security/service/ProtectionModeService.kt`
- Emergency: `app/src/main/java/com/wype/security/service/EmergencySmsService.kt`, `app/src/main/java/com/wype/security/service/GoogleBackupService.kt`, `app/src/main/java/com/wype/security/service/FactoryResetService.kt`
- Admin/boot/watchdog: `app/src/main/java/com/wype/security/admin/WypeDeviceAdminReceiver.kt`, `app/src/main/java/com/wype/security/receiver/BootReceiver.kt`, `app/src/main/java/com/wype/security/service/ServiceWatchdog.kt`
- UI/config: `app/src/main/java/com/wype/security/ui/MainActivity.kt`, `app/src/main/java/com/wype/security/utils/PreferencesManager.kt`
- Backend: `backend/package.json`, `backend/tsconfig.json`, `backend/src/index.ts`
- Provisioning assets: `scripts/generate_wype_do_qr.ps1`, `scripts/provisioning.json`, `templates/do_provisioning_email.html`

Notes
- Release signing is configured in `app/build.gradle.kts`; for development use debug builds. Do not modify signing configs here.
- Android build config defines `BuildConfig.API_BASE_URL` (default `https://api.wype.app`).
