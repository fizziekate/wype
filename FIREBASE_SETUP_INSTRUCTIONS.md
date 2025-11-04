# Firebase Setup Instructions for WYPE Security App

## ⚠️ CRITICAL: Your current Firebase configuration is using PLACEHOLDER DATA and needs to be replaced with real Firebase project configuration.

### Current Issues:
- ❌ `google-services.json` contains dummy data
- ❌ No real Firebase project connected
- ❌ API keys are placeholders
- ❌ Project ID is a placeholder

## Step-by-Step Setup:

### 1. Create Firebase Project
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Create a project"
3. Project name: `WYPE Security` (or your preferred name)
4. Enable Google Analytics (recommended)
5. Choose or create Google Analytics account

### 2. Add Android App
1. Click "Add app" → Android
2. **Android package name**: `com.wype.security` (MUST MATCH EXACTLY)
3. **App nickname**: `WYPE Security App`
4. **Debug signing certificate SHA-1** (optional for now, required for release):
   - Get from: `./gradlew signingReport`
   - Or: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`

### 3. Download Configuration File
1. Download the `google-services.json` file
2. **Replace** the current placeholder file: `app/google-services.json`
3. **Important**: The new file should have:
   - Real project ID (not "wype-security-placeholder")
   - Real API keys (not "AIzaSy-placeholder...")
   - Real client IDs (not all zeros)

### 4. Enable Firebase Services
In the Firebase Console, enable these services:

#### Authentication:
- Go to Authentication → Sign-in method
- Enable "Google" provider
- Add your app's SHA-1 certificate fingerprint

#### Firestore Database:
- Go to Firestore Database
- Create database (start in test mode)
- Location: Choose closest to your users

#### Storage:
- Go to Storage
- Get started with default rules

#### Crashlytics (Optional but Recommended):
- Go to Crashlytics
- Enable Crashlytics

### 5. Configure Security Rules

#### Firestore Security Rules:
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can read/write their own backup data
    match /users/{userId}/backups/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Emergency logs (read-only for authenticated users)
    match /emergency_logs/{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

#### Storage Security Rules:
```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /users/{userId}/backups/{allPaths=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

### 6. Test Firebase Connection
After replacing `google-services.json`, build the project:
```bash
./gradlew build
```

Look for Firebase initialization in logs:
```
FirebaseApp: Firebase API initialization successful
```

### 7. Google Services Configuration
Your current Google Services dependencies are correct:
- ✅ Google Sign-In
- ✅ Google Drive API  
- ✅ Firebase BOM and services

### 8. Permissions Check
Your app already has the necessary permissions in AndroidManifest.xml:
- ✅ Internet access
- ✅ Google account access
- ✅ Network state access

## What This Enables:

### For Google Backup Service:
- ✅ **Real Firebase Authentication**: Users can sign in with Google
- ✅ **Firestore Database**: Store backup metadata and emergency logs
- ✅ **Firebase Storage**: Store encrypted backup files
- ✅ **Google Drive Integration**: Backup to user's Google Drive

### For Emergency Features:
- ✅ **Crash Reporting**: Track any issues with emergency systems
- ✅ **Analytics**: Monitor emergency activations (anonymous)
- ✅ **Remote Config**: Update emergency settings remotely
- ✅ **Cloud Functions**: Trigger additional emergency actions

## Testing Your Setup:

1. Replace the placeholder `google-services.json`
2. Build the project: `./gradlew build`  
3. Run the app
4. Check logs for Firebase initialization
5. Test Google Sign-In in the backup screen
6. Verify emergency backup functionality

## Security Notes:

- 🔐 Firebase project should be in production mode for release
- 🔐 Enable App Check for additional security
- 🔐 Configure proper security rules
- 🔐 Use Firebase Performance Monitoring to track emergency response times
- 🔐 Set up Firebase Alerts for emergency activations

## After Setup is Complete:

Your Firebase integration will provide:
- **Secure cloud backup** before device wipe
- **Emergency activity logging** for forensics
- **Crash reporting** if emergency systems fail
- **Remote configuration** of emergency parameters
- **Analytics** on emergency system usage (anonymous)

Remember: The app will work without Firebase (emergency SMS and factory reset will still function), but backup features require proper Firebase configuration.
