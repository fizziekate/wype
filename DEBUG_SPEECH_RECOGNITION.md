# Speech Recognition Debugging Guide

## Overview
The SilentSpeechService has been enhanced with comprehensive debugging logs. If speech recognition isn't working, follow this guide to identify the issue.

## Quick Debugging Steps

### 1. Connect Your Device
```bash
adb devices
```
Ensure your device is connected and shows up in the list.

### 2. Check if Service is Running
```bash
# Check if the app is installed and running
adb shell ps | findstr com.wype.security

# Or check running services
adb shell dumpsys activity services | findstr SilentSpeechService
```

### 3. Monitor Live Logs
```bash
# Filter for our service logs
adb logcat -s SilentSpeechService

# Or for all speech-related logs
adb logcat | findstr -i "speech\|recognition\|wype"
```

### 4. Check App Permissions
```bash
# Check if RECORD_AUDIO permission is granted
adb shell dumpsys package com.wype.security | findstr "RECORD_AUDIO"
```

### 5. Test Speech Recognition Availability
```bash
# Check if Google Speech Services are installed
adb shell pm list packages | findstr speech
```

## What the Debug Logs Will Show

When the service starts, you should see these logs:

1. **Service Creation**:
   ```
   DEBUG: Service onCreate() called
   DEBUG: Wake phrase configured: '[your phrase]' (X chars)
   DEBUG: Has wake phrase: true/false
   DEBUG: Service enabled: true/false
   ```

2. **Speech Recognition Initialization**:
   ```
   DEBUG: Initializing SpeechRecognizer...
   DEBUG: SpeechRecognizer initialized successfully
   ```

3. **When Audio Listening Starts**:
   ```
   DEBUG: startSpeechRecognition() called
   DEBUG: Starting speech recognition with language: [locale]
   DEBUG: Speech recognition started successfully for emergency phrase detection
   ```

4. **When Speech is Detected**:
   ```
   Speech recognized: '[what was heard]'
   DEBUG: Checking wake phrase - spoken: '[heard]', saved: '[configured]'
   DEBUG: Phrase similarity check - result: true/false
   ```

## Common Issues and Solutions

### Issue 1: No Wake Phrase Configured
**Symptoms**: Logs show `DEBUG: No saved wake phrase found in preferences`
**Solution**: Set up your emergency phrase in the app settings

### Issue 2: Speech Recognition Not Available
**Symptoms**: Logs show `DEBUG: Speech recognition not available on this device`
**Solutions**: 
- Install Google app or Google Speech Services
- Ensure device has microphone access
- Try on a different device

### Issue 3: Permission Denied
**Symptoms**: Logs show `Permission error - RECORD_AUDIO not granted`
**Solution**: Grant microphone permission to the app in Android settings

### Issue 4: Service Not Starting
**Symptoms**: No logs from SilentSpeechService appear
**Solutions**:
- Start the service manually from the app
- Check if the app has background activity restrictions
- Ensure foreground service permissions are granted

### Issue 5: Speech Recognition Errors
**Symptoms**: Logs show speech errors like `ERROR_NO_MATCH` or `ERROR_SPEECH_TIMEOUT`
**Solutions**:
- Speak more clearly and loudly
- Check if there's background noise
- Ensure your phrase is at least 3 characters long
- The service auto-restarts recognition after these errors

## Testing Procedure

1. **Install and grant permissions** to the app
2. **Configure your emergency phrase** in app settings
3. **Start the service** from the app
4. **Open terminal** and run: `adb logcat -s SilentSpeechService`
5. **Say your phrase twice** within 10 seconds
6. **Check logs** for the detection sequence

### Expected Log Sequence for Working Detection:
```
Speech recognized: 'help me now'
DEBUG: Checking wake phrase - spoken: 'help me now', saved: 'help me now'
DEBUG: Phrase similarity check - result: true
Wake phrase detected: 'help me now' matches 'help me now'
First wake phrase detection

[After second phrase within 10 seconds]
Speech recognized: 'help me now'
Wake phrase detected: 'help me now' matches 'help me now'
DOUBLE WAKE PHRASE DETECTED - EMERGENCY TRIGGERED
EMERGENCY SEQUENCE TRIGGERED!
```

## Manual Testing Without Device Connection

If you can't connect ADB, you can:

1. **Enable Developer Options** on your device
2. **Enable "Show taps"** and **"Show touches"** in Developer Options
3. **Use a log viewer app** like "Log Viewer" from Play Store
4. **Filter logs** for "SilentSpeechService"
5. **Test speech recognition** and observe the logs

## Contact Information

If you're still having issues after following this guide, the debug logs will help identify the specific problem area. Share the logs showing:
- Service startup sequence
- Speech recognition initialization
- What happens when you speak your phrase
