@echo off
echo =======================================================
echo 🚨 ENABLING EMERGENCY PHRASE DETECTION
echo =======================================================
echo.

echo ℹ️  EXPLANATION:
echo Your beeping issue has been FIXED, but emergency detection
echo was temporarily disabled to stop the beeping.
echo.
echo Now we're re-enabling emergency detection using the
echo WYPE Accessibility Service, which doesn't cause beeping.
echo.

echo =======================================================
echo 📱 STEP 1: Enable Accessibility Service
echo =======================================================
echo.

echo Opening Android Settings...
echo Please follow these steps:
echo.
echo 1. Go to Settings > Accessibility
echo 2. Find "Wype" in the list
echo 3. Turn ON the Wype accessibility service
echo 4. Grant the permissions when prompted
echo.

adb shell am start -a android.settings.ACCESSIBILITY_SETTINGS 2>nul
timeout /t 5 /nobreak >nul

echo.
echo =======================================================
echo 🔧 STEP 2: Configure Emergency Settings
echo =======================================================
echo.

echo Opening Wype app to check emergency settings...
adb shell am start -n com.wype.security/.ui.MainActivity 2>nul
timeout /t 3 /nobreak >nul

echo.
echo Please check these settings in your Wype app:
echo.
echo ✅ Wake Phrase: Set your emergency phrase
echo ✅ Buddy Contact: Configure emergency contact
echo ✅ Service Enabled: Make sure it's turned ON
echo ✅ Protection Mode: Enable if available
echo.

echo =======================================================
echo 🔊 TESTING EMERGENCY DETECTION
echo =======================================================
echo.

echo HOW TO TEST:
echo.
echo 1. Say your emergency phrase TWICE within 30 seconds
echo 2. Wait for confirmation (you should see logs)
echo 3. Check if emergency SMS is sent
echo.
echo IMPORTANT: 
echo - NO beeping sounds should occur
echo - Detection uses Accessibility Service (more reliable)
echo - 5-minute cooldown between emergency triggers
echo - Maximum 3 emergencies per day
echo.

echo =======================================================
echo 🔍 TROUBLESHOOTING
echo =======================================================
echo.

echo If emergency detection doesn't work:
echo.
echo 1. CHECK: Accessibility service is enabled
echo    Settings > Accessibility > Wype > ON
echo.
echo 2. CHECK: Microphone permission granted
echo    Settings > Apps > Wype > Permissions > Microphone
echo.
echo 3. CHECK: Wake phrase is configured in app
echo    Open Wype > Home > Set Emergency Phrase
echo.
echo 4. CHECK: Buddy contact is set
echo    Open Wype > Settings > Emergency Contact
echo.
echo 5. TRY: Restart the app after enabling accessibility
echo.
echo 6. CHECK: No other speech apps are interfering
echo.

echo =======================================================
echo ✅ SUMMARY
echo =======================================================
echo.
echo ✅ Beeping issue: FIXED (no more annoying sounds)
echo ✅ Emergency detection: Available via Accessibility Service
echo ✅ Detection method: Double phrase confirmation (30s window)
echo ✅ Safety features: 5min cooldown, 3 per day limit
echo ✅ Reliability: Accessibility service is more stable
echo.

echo Your app should now detect emergency phrases WITHOUT beeping!
echo.
pause
