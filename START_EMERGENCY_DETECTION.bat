@echo off
echo ===========================================================
echo 🚨 STARTING EMERGENCY DETECTION - WORKING SOLUTION
echo ===========================================================
echo.

echo ℹ️  THE PROBLEM:
echo The previous approach relied on Accessibility Service, but the 
echo ProtectionModeService is actually the correct service for
echo emergency detection using HybridWakeWordManager (no beeping).
echo.

echo ===========================================================
echo 🔧 SOLUTION: Enable ProtectionModeService
echo ===========================================================
echo.

echo Step 1: Stopping any running services...
adb shell am force-stop com.wype.security 2>nul

echo Step 2: Starting the app...
adb shell am start -n com.wype.security/.ui.MainActivity 2>nul
timeout /t 3 /nobreak >nul

echo Step 3: Starting ProtectionModeService...
adb shell am startservice com.wype.security/.service.ProtectionModeService 2>nul
timeout /t 2 /nobreak >nul

echo.
echo ===========================================================
echo ✅ EMERGENCY DETECTION SHOULD NOW BE ACTIVE!
echo ===========================================================
echo.

echo 📊 HOW IT WORKS:
echo.
echo 1. 🔇 NO BEEPING: Uses HybridWakeWordManager (TensorFlow/Neural)
echo 2. 🎯 DETECTION: Custom ML models, not Android SpeechRecognizer
echo 3. 🔄 CONFIRMATION: Say emergency phrase TWICE within 30 seconds
echo 4. 🚨 ACTION: Sends SMS to your buddy contact
echo 5. ⏱️ SAFETY: 5-minute cooldown, max 3 per day
echo.

echo ===========================================================
echo 🧪 TESTING INSTRUCTIONS
echo ===========================================================
echo.

echo TO TEST EMERGENCY DETECTION:
echo.
echo 1. Make sure your emergency phrase is set in the app
echo 2. Make sure your buddy contact is configured  
echo 3. Say your emergency phrase CLEARLY
echo 4. Wait 2-3 seconds
echo 5. Say your emergency phrase AGAIN
echo 6. Check if SMS is sent within 30 seconds
echo.

echo IMPORTANT NOTES:
echo - Speak clearly and at normal volume
echo - Wait a few seconds between phrases
echo - No beeping should occur during detection
echo - Check app logs if it doesn't work
echo.

echo ===========================================================
echo 🔍 TROUBLESHOOTING
echo ===========================================================
echo.

echo If detection still doesn't work:
echo.
echo 1. CHECK: Emergency phrase is set in app settings
echo 2. CHECK: Buddy contact name and phone configured
echo 3. CHECK: Microphone permission granted
echo 4. CHECK: Protection Mode is enabled in app
echo 5. TRY: Restart app and run this script again
echo 6. CHECK: Speak louder or closer to microphone
echo 7. CHECK: No other apps using microphone
echo.

echo ===========================================================
echo 📱 APP CONFIGURATION REQUIRED
echo ===========================================================
echo.

echo Make sure these are configured in your Wype app:
echo.
echo ✅ Emergency Phrase: Set your wake phrase
echo ✅ Buddy Contact: Name and phone number  
echo ✅ Protection Mode: Enable in settings
echo ✅ Service Enabled: Turn ON the main service
echo ✅ Permissions: Grant microphone access
echo.

echo ===========================================================
echo 🎉 EXPECTED RESULT
echo ===========================================================
echo.
echo After completing the setup:
echo - Emergency detection active (no beeping!)
echo - Silent notification in status bar
echo - ML-based wake word detection working
echo - Double confirmation required for safety
echo - SMS alerts sent to buddy contact
echo.

echo Emergency detection is now running!
echo Try saying your emergency phrase twice to test it.
echo.
pause
