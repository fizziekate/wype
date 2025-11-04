# Google Drive API Configuration Instructions

## Issue with Current Setup
Your current `google-services.json` file is missing the OAuth client configuration needed for Google Drive API access. The `oauth_client` array is empty, which will prevent the Google Sign-In and Drive API from working properly.

## Steps to Fix

### 1. Access Google Cloud Console
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project "wype-security" (Project ID: wype-security)

### 2. Enable Google Drive API
1. In the left sidebar, go to "APIs & Services" > "Library"
2. Search for "Google Drive API"
3. Click on it and press "Enable" if not already enabled

### 3. Configure OAuth Consent Screen
1. Go to "APIs & Services" > "OAuth consent screen"
2. Fill in the required information:
   - App name: "WYPE Security"
   - User support email: Your email
   - Developer contact: Your email
3. Add scopes:
   - `../auth/drive.file` (View and manage Google Drive files and folders that you have opened or created with this app)
4. Save and continue through the setup

### 4. Create OAuth 2.0 Credentials
1. Go to "APIs & Services" > "Credentials"
2. Click "Create Credentials" > "OAuth 2.0 Client ID"
3. Select "Android" as application type
4. Enter:
   - Name: "WYPE Android Client"
   - Package name: `com.wype.security`
   - SHA-1 certificate fingerprint: You need to generate this

### 5. Get SHA-1 Certificate Fingerprint
Run this command in your project directory to get the debug SHA-1:
```bash
.\gradlew.bat signingReport
```
Look for the "debug" variant and copy the SHA-1 fingerprint.

For release builds, you'll need the SHA-1 from your release keystore:
```bash
keytool -list -v -keystore app/keystore/wype-release.jks -alias wypekey
```
(Password: Penny$1977)

### 6. Update google-services.json
1. After creating the OAuth client, go back to Firebase Console
2. Go to Project Settings > General tab
3. Scroll down to "Your apps" section
4. Click the download icon next to your Android app to get the updated `google-services.json`
5. Replace your current `app/google-services.json` with the new one

### 7. Alternative: Manual Configuration
If you prefer to manually add the OAuth client to your existing `google-services.json`, add this structure to the `oauth_client` array:

```json
{
  "oauth_client": [
    {
      "client_id": "YOUR_CLIENT_ID.apps.googleusercontent.com",
      "client_type": 1,
      "android_info": {
        "package_name": "com.wype.security",
        "certificate_hash": "YOUR_SHA1_FINGERPRINT"
      }
    }
  ]
}
```

## Testing the Fix
After updating the configuration:
1. Rebuild the app: `.\gradlew.bat assembleDebug`
2. Install on your device
3. Go to the Backup tab and try signing in to Google
4. Test the manual backup feature
5. Check that backup files appear in your Google Drive under "WYPE Emergency Backup" folder

## Security Note
The backup functionality is designed to work during emergency situations. Make sure:
- Google account is signed in on the device
- Backup is enabled in the app settings
- Device has internet connection during emergency
- Google Drive has sufficient storage space

The emergency backup will create a timestamped file containing your configuration and emergency contact details, but no sensitive data like the actual wake phrase is included.
