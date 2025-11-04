Wype DO QR generation

1) Edit scripts\provisioning.json with your final provisioning payload (Android Managed Provisioning JSON).
   Required fields (examples shown as placeholders):
   - android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME
   - android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION
   - android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM (base64 of SHA-256)
   - android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED (true for DO)
   Optional extras:
   - android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE (e.g., enrollment_token, provisioning_url)

2) Generate QR PNG (requires Node.js; uses 'npx qrcode'):
   pwsh -File .\scripts\generate_wype_do_qr.ps1 -JsonPath .\scripts\provisioning.json -OutputPath .\templates\assets\wype_do_qr.png

   Alternatively, pick a PowerShell QR module and adapt the script.

3) Embed the PNG in the HTML email (as CID attachment or hosted link) or inline as data URI.

Note: Universal QR is acceptable. Identity happens after DO provisioning.
