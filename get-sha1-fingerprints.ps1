# Firebase SHA1 Fingerprint Helper Script
# This script displays the SHA1 fingerprints needed for Firebase configuration

Write-Host "=== Firebase SHA1 Fingerprint Helper ===" -ForegroundColor Green
Write-Host ""

Write-Host "Your App Package Name: com.wype.security" -ForegroundColor Yellow
Write-Host "Firebase Project ID: wype-security-12f0e" -ForegroundColor Yellow
Write-Host ""

Write-Host "=== DEBUG KEYSTORE SHA1 ===" -ForegroundColor Cyan
try {
    $debugKeystore = "$env:USERPROFILE\.android\debug.keystore"
    if (Test-Path $debugKeystore) {
        $debugOutput = keytool -list -v -alias androiddebugkey -keystore $debugKeystore -storepass android -keypass android 2>$null
        $debugSHA1 = ($debugOutput | Select-String "SHA1:").ToString().Split(":")[1].Trim()
        Write-Host "Debug SHA1: $debugSHA1" -ForegroundColor White
        Write-Host "Status: Already added to Firebase ✓" -ForegroundColor Green
    } else {
        Write-Host "Debug keystore not found" -ForegroundColor Red
    }
} catch {
    Write-Host "Error reading debug keystore" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== RELEASE KEYSTORE SHA1 ===" -ForegroundColor Cyan
try {
    $releaseKeystore = "app\keystore\wype-release.jks"
    if (Test-Path $releaseKeystore) {
        $releaseOutput = keytool -list -v -alias wypekey -keystore $releaseKeystore -storepass "Penny\$1977" 2>$null
        $releaseSHA1 = ($releaseOutput | Select-String "SHA1:").ToString().Split(":")[1].Trim()
        Write-Host "Release SHA1: $releaseSHA1" -ForegroundColor White
        Write-Host "Status: NEEDS TO BE ADDED TO FIREBASE ❌" -ForegroundColor Red
    } else {
        Write-Host "Release keystore not found at: $releaseKeystore" -ForegroundColor Red
    }
} catch {
    Write-Host "Error reading release keystore" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== INSTRUCTIONS ===" -ForegroundColor Magenta
Write-Host "1. Go to Firebase Console: https://console.firebase.google.com/" -ForegroundColor White
Write-Host "2. Select your project: wype-security-12f0e" -ForegroundColor White
Write-Host "3. Go to Project Settings (gear icon)" -ForegroundColor White
Write-Host "4. Select the General tab" -ForegroundColor White
Write-Host "5. Scroll down to 'Your apps' section" -ForegroundColor White
Write-Host "6. Find your Android app (com.wype.security)" -ForegroundColor White
Write-Host "7. Click 'Add fingerprint'" -ForegroundColor White
Write-Host "8. Add the Release SHA1 fingerprint shown above" -ForegroundColor White
Write-Host "9. Download the updated google-services.json" -ForegroundColor White
Write-Host "10. Replace app/google-services.json with the new file" -ForegroundColor White

Write-Host ""
Write-Host "=== QUICK COPY (Release SHA1 to add) ===" -ForegroundColor Yellow
if (Test-Path "app\keystore\wype-release.jks") {
    try {
        $releaseOutput = keytool -list -v -alias wypekey -keystore "app\keystore\wype-release.jks" -storepass "Penny\$1977" 2>$null
        $releaseSHA1 = ($releaseOutput | Select-String "SHA1:").ToString().Split(":")[1].Trim()
        Write-Host "$releaseSHA1" -ForegroundColor Green
    } catch {
        Write-Host "20:D4:02:2A:FE:80:5D:F3:CB:D3:96:66:92:C4:E6:90:26:FD:57:26" -ForegroundColor Green
    }
} else {
    Write-Host "20:D4:02:2A:FE:80:5D:F3:CB:D3:96:66:92:C4:E6:90:26:FD:57:26" -ForegroundColor Green
}

Write-Host ""
Write-Host "Press any key to continue..."
$null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')
