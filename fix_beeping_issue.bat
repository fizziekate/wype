@echo off
echo ==============================================
echo WYPE APP BEEPING ISSUE FIX
echo ==============================================
echo.

echo Step 1: Stopping all Wype services...
adb shell am force-stop com.wype.security
timeout /t 2 /nobreak >nul

echo Step 2: Clearing app data to reset audio settings...
adb shell pm clear com.wype.security
timeout /t 2 /nobreak >nul

echo Step 3: Restarting the app...
adb shell am start -n com.wype.security/.ui.MainActivity
timeout /t 3 /nobreak >nul

echo.
echo ==============================================
echo BEEPING SHOULD NOW BE STOPPED
echo ==============================================
echo.
echo If beeping continues, follow these steps:
echo.
echo 1. Open Wype app on your device
echo 2. Go to Settings / Home screen
echo 3. Turn OFF "Service Enabled" toggle
echo 4. Turn OFF "Protection Mode" if enabled
echo 5. Disable any speech recognition services
echo.
echo TECHNICAL EXPLANATION:
echo - Multiple speech services were running simultaneously
echo - Android's SpeechRecognizer was generating system beeps
echo - Audio focus conflicts caused additional sounds
echo.
echo PERMANENT SOLUTION:
echo - Use only ONE speech service at a time
echo - Prefer offline speech recognition
echo - Test audio settings before enabling services
echo.
echo Press any key to open device settings for further configuration...
pause >nul

echo Opening Android Settings - Audio...
adb shell am start -a android.settings.SOUND_SETTINGS
timeout /t 2 /nobreak >nul

echo.
echo Fix completed! Check your device for silence.
echo.
pause
