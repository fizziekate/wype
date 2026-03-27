# WypeFactoryReset_REGISTER_FIRST

README for local project path:
`C:\Users\Felicity\WypeFactoryReset_REGISTER_FIRST`

## Overview

This is now an **Android app project** for a "register first" flow that is suitable as a base for Google Play distribution.

The app currently includes:
- a working Android `app` module
- a register-first UI (`MainActivity`) where a user enters a name and device ID
- validation + normalization logic in `RegisterFirstService`
- unit tests for core registration logic
- CI build/test checks in GitHub Actions

## Prerequisites

- Android Studio (latest stable)
- Android SDK installed (API 35 and build-tools via Android Studio)
- JDK 17+ (JDK 21 also works in this repo)
- Git (optional, for version control)

## Quick Start (Windows)

Open PowerShell in:

```powershell
cd C:\Users\Felicity\WypeFactoryReset_REGISTER_FIRST
```

Then run:

```powershell
.\gradlew.bat tasks
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

## Quick Start (macOS/Linux)

```bash
./gradlew tasks
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

## Run on a Device/Emulator

From Android Studio:
1. Open this project folder.
2. Let Gradle sync.
3. Select an emulator or connected Android device.
4. Run the `app` configuration.

Or from command line:

- Windows:
  ```powershell
  .\gradlew.bat :app:installDebug
  ```
- macOS/Linux:
  ```bash
  ./gradlew :app:installDebug
  ```

## Build Artifacts

- Debug APK:
  - `app/build/outputs/apk/debug/app-debug.apk`

For Play Store release, you will typically generate a signed AAB:

- Windows:
  ```powershell
  .\gradlew.bat :app:bundleRelease
  ```
- macOS/Linux:
  ```bash
  ./gradlew :app:bundleRelease
  ```

## Project Structure

```text
WypeFactoryReset_REGISTER_FIRST/
├─ .github/
│  └─ workflows/
│     └─ ci.yml
├─ app/
│  ├─ src/
│  │  ├─ main/
│  │  │  ├─ java/com/wype/registerfirst/
│  │  │  ├─ res/
│  │  │  └─ AndroidManifest.xml
│  │  └─ test/
│  │     └─ java/com/wype/registerfirst/
│  ├─ build.gradle.kts
│  └─ proguard-rules.pro
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle.properties
├─ gradle/
│  └─ wrapper/
├─ gradlew
├─ gradlew.bat
├─ LICENSE
└─ README.md
```

## CI

GitHub Actions workflow (`.github/workflows/ci.yml`) runs:
- `:app:assembleDebug`
- `:app:testDebugUnitTest`

on pushes and pull requests.

## Next Play Store Steps

Before publishing to Google Play, you should still:
1. Set your final `applicationId` and app branding.
2. Add a signed release configuration (keystore).
3. Enable Play App Signing in Play Console.
4. Prepare store listing assets and privacy policy.
5. Run device testing and optionally add instrumentation tests.

## License

This project is licensed under the **MIT License**.
See the [LICENSE](LICENSE) file for details.