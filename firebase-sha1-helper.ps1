# Firebase SHA1 Fingerprint Helper Script
Write-Host "=== Firebase SHA1 Fingerprint Helper ===" -ForegroundColor Green
Write-Host ""
Write-Host "Your App Package Name: com.wype.security" -ForegroundColor Yellow
Write-Host "Firebase Project ID: wype-security-12f0e" -ForegroundColor Yellow
Write-Host ""

# Display the SHA1 that needs to be added
Write-Host "=== SHA1 TO ADD TO FIREBASE ===" -ForegroundColor Red
Write-Host "20:D4:02:2A:FE:80:5D:F3:CB:D3:96:66:92:C4:E6:90:26:FD:57:26" -ForegroundColor Green
Write-Host ""

Write-Host "=== INSTRUCTIONS ===" -ForegroundColor Magenta
Write-Host "1. Copy the SHA1 above (green text)" -ForegroundColor White
Write-Host "2. Go to: https://console.firebase.google.com/" -ForegroundColor White
Write-Host "3. Select your project: wype-security-12f0e" -ForegroundColor White
Write-Host "4. Click the gear icon (Project Settings)" -ForegroundColor White
Write-Host "5. Go to General tab" -ForegroundColor White
Write-Host "6. Scroll to 'Your apps' section" -ForegroundColor White
Write-Host "7. Find com.wype.security app" -ForegroundColor White
Write-Host "8. Click 'Add fingerprint'" -ForegroundColor White
Write-Host "9. Paste the SHA1 from step 1" -ForegroundColor White
Write-Host "10. Download new google-services.json" -ForegroundColor White
Write-Host "11. Replace app/google-services.json" -ForegroundColor White
Write-Host ""
Write-Host "This will fix the 'app already exists' error!" -ForegroundColor Cyan
