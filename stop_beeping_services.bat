@echo off
echo Stopping Wype services that may be causing beeps...

REM Kill any running Wype app processes
echo Stopping Wype app...
adb shell am force-stop com.wype.security

echo Services stopped. Beeping should cease.
echo.
echo To prevent beeps from returning:
echo 1. Open Wype app
echo 2. Go to Settings
echo 3. Temporarily disable "Service Enabled" option
echo 4. Or disable speech recognition services
echo.
pause
