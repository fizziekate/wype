# WYPE Security App

A security app that listens for a custom wake phrase and performs emergency actions including SMS alerts and factory reset.

## 🚀 Checkpoint 1 - COMPLETED ✅
## 🚀 Checkpoint 2 - COMPLETED ✅
## 🚀 Checkpoint 3 - COMPLETED ✅

### ✅ What's Done (Checkpoints 1-3):
- **Complete Android project scaffold** with modern architecture
- **Bottom Navigation** with 5 screens (Home, Instructions, Record, Buddy, Backup)
- **Material Design 3** theming and UI components
- **Navigation Component** setup with fragments
- **Permission declarations** in AndroidManifest.xml
- **Full Home screen** with service controls and setup status tracking
- **Instructions screen** with step-by-step guide
- **Complete Record/Play screen** with audio recording and wake phrase setup
- **Background Speech Service** with continuous listening capability
- **Wake phrase detection** with double-detection logic (10-second window)
- **Complete Buddy Contact System** with contact picker and SMS functionality
- **Emergency SMS Service** with location information and delivery tracking
- **Integrated Emergency Sequence** - speech detection triggers SMS alerts
- **Audio recording/playback** with quality assessment
- **Preferences management** for all app settings
- **Permission handling** with runtime requests for microphone, SMS, contacts
- **Boot receiver** to restart service after device restart

### 🏗️ Architecture:
- **MVVM** pattern with ViewModels and LiveData
- **Navigation Component** for screen transitions
- **Data Binding** for efficient UI updates
- **Services** for background speech recognition
- **BroadcastReceivers** for device admin and boot events
- **Modern Android** development practices

### 📱 Screens Implemented:
1. **Home** - Service status, controls, and quick setup with setup status tracking
2. **Instructions** - Step-by-step usage guide with warnings
3. **Record** - Complete wake phrase recording and playback functionality ✅
4. **Buddy** - Complete contact selection and SMS emergency system ✅
5. **Backup** - Complete Google Drive backup and recovery system ✅

### 🔧 Technical Features:
- **Target SDK**: Android 14 (API 34)
- **Min SDK**: Android 7.0 (API 24)
- **Language**: Kotlin 100%
- **Build System**: Gradle with Kotlin DSL
- **Speech Recognition**: Android SpeechRecognizer API with continuous listening ✅
- **Audio Recording**: MediaRecorder with quality assessment ✅
- **Background Service**: Foreground service with notification ✅
- **Wake Phrase Detection**: Fuzzy matching with double-detection logic ✅
- **Emergency SMS System**: Contact picker, test messages, location tracking ✅
- **Permission Handling**: Runtime permission requests (microphone, SMS, contacts) ✅
- **Auto-restart**: Boot receiver for service persistence ✅
- **Device Admin**: Factory reset capability with device administrator privileges ✅
- **Google Backup**: Automatic emergency data backup to Google Drive ✅
- **Complete Emergency Sequence**: Speech → SMS → Backup → Factory Reset ✅

## ✅ Checkpoint 4 Status: COMPLETED

### 🎯 Completed Features:
1. **Device Admin Integration** ✅
   - WypeDeviceAdminReceiver with factory reset capability
   - Device administrator privilege handling
   - Factory reset triggering from emergency sequence
   - Device lock functionality

2. **Google Backup System** ✅
   - GoogleBackupService with Google Drive API integration
   - Automatic data backup before factory reset
   - BackupFragment with complete UI for Google account management
   - BackupViewModel for state management
   - Backup status tracking and progress indication

3. **Complete Emergency Sequence** ✅
   - Integrated SMS → Backup → Factory Reset workflow
   - Emergency backup triggers factory reset automatically
   - Fallback factory reset if backup fails
   - Full end-to-end emergency sequence implementation

### 📋 Checkpoint 4 Implementation:
- [x] WypeDeviceAdminReceiver with factory reset capability
- [x] GoogleBackupService for emergency data backup to Google Drive
- [x] BackupFragment UI with Google sign-in and backup controls
- [x] BackupViewModel for backup state management
- [x] Google account integration with sign-in/sign-out functionality
- [x] Backup progress tracking and status display
- [x] Integration of backup service with emergency sequence
- [x] Factory reset triggering after backup completion
- [x] Material Design 3 UI for backup functionality
- [x] Comprehensive backup information and status display

## 🏃‍♂️ Next Steps - Checkpoint 5 (Future Enhancement):

### 🎯 Potential Future Goals:
1. **Advanced Security Features**
   - Biometric authentication for settings access
   - Encrypted backup data
   - Remote device management

2. **Enhanced Emergency Features**
   - Multiple emergency contacts
   - Escalating alert system
   - Video/photo capture before wipe

3. **Advanced Configuration**
   - Custom timeout settings
   - Different wake phrases for different actions
   - Advanced speech recognition models

## 🚀 How to Run:

### Prerequisites:
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- Kotlin plugin enabled

### Setup Steps:
1. Open Android Studio
2. Import the project from `C:\Users\Felicity\WypeApp`
3. Wait for Gradle sync to complete
4. Connect an Android device or start an emulator
5. Run the app (Shift+F10)

### Testing Checkpoint 3:
- Navigate between screens using bottom navigation
- **Record Screen**: Record wake phrase, test playback, save phrase
- **Buddy Screen**: Select contact, send test SMS, verify contact display
- **Home Screen**: Toggle speech service, verify setup status tracking
- **Permissions**: Grant microphone, SMS, and contacts permissions when prompted
- **Speech Recognition**: Test wake phrase detection (say phrase twice in 10 seconds)
- **Emergency SMS**: Verify SMS is sent to buddy when double detection occurs
- **Background Service**: Verify service runs in background with notification
- **Boot Persistence**: Restart device, verify service auto-starts if enabled

## ⚠️ Important Notes:

### Build Status: ✅ FULLY SUCCESSFUL
- **Build System**: Gradle 9.0-milestone-1 with Kotlin DSL
- **Compilation**: SUCCESS - All files compile without errors
- **SDK Compatibility**: Fixed Android Gradle Plugin to 8.5.2 with Java 17
- **Lint Issues**: RESOLVED - Lint baseline created for known non-critical issues
- **API Compatibility**: Fixed Android API level checks (24+ support)
- **APK Generation**: Both DEBUG and RELEASE APKs build successfully
- **Full Build Command**: `gradlew build` passes completely

### Security Considerations:
- This app requires dangerous permissions (RECORD_AUDIO, SEND_SMS)
- Device admin capabilities can factory reset the device
- Only use for legitimate emergency scenarios
- Test thoroughly on development devices only

### Development Environment:
- **Android Studio**: Hedgehog (2023.1.1) or newer recommended
- **Java**: OpenJDK 17 (Temurin)
- **Build Tools**: Android Gradle Plugin 8.5.2
- Use emulator for initial development and testing
- Real device testing required for speech recognition
- Factory reset testing should be done on disposable devices
- Keep multiple backup devices for development

### Permissions Required:
- **RECORD_AUDIO** - For speech recognition
- **SEND_SMS** - For emergency contact alerts
- **DEVICE_ADMIN** - For factory reset capability
- **FOREGROUND_SERVICE** - For background listening
- **BOOT_COMPLETED** - For auto-restart after reboot
- **ACCESS_FINE_LOCATION** - For emergency SMS location data

## 🧪 Testing Plan:

### Checkpoint 1 Testing:
- [x] App launches successfully
- [x] Bottom navigation works
- [x] All screens load without crashes
- [x] Material Design theme applied
- [x] Service toggle button responds (placeholder)
- [x] Instructions screen displays correctly

### Future Testing Requirements:
- **Checkpoint 2**: Speech recognition accuracy, background service stability
- **Checkpoint 3**: SMS sending, contact selection, buddy system
- **Checkpoint 4**: Factory reset functionality (EXTREME CAUTION)
- **Checkpoint 5**: End-to-end emergency scenario testing

## 📄 Project Structure:
```
WypeApp/
├── app/
│   ├── src/main/
│   │   ├── java/com/wype/security/
│   │   │   ├── ui/               # UI fragments and activities
│   │   │   ├── service/          # Background services
│   │   │   ├── admin/            # Device admin receivers
│   │   │   ├── receiver/         # Broadcast receivers
│   │   │   └── WypeApplication.kt
│   │   ├── res/                  # Resources (layouts, strings, etc.)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---
**⚠️ WARNING: This app is designed for emergency situations and includes destructive capabilities. Use extreme caution during development and testing.**
