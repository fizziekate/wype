# Wype — First-Run & Device Owner (DO) Flow

Repo root (Windows): C:\Users\Felicity\WypeApp

## Key UI screens (PNG)
Registration:
- do_register.png (blank form)
- register_clicked.png (CTA active style)

Home (instructions):
- do_home_pressed.png (aka do_home-pressed_pressed.png in some exports)

Google backup:
- do_google_backup_unclicked.png
- do_google_backup_clicked.png

Note: Optional DO template backgrounds are purely visual; logic below drives when each is shown.

---

## First Install — Required User Journey

1) Registration screen → do_register.png
- User enters Email, Phone No, Password.
- On submit: create account (backend) → persist userId, email, phone_e164 (no phrases/buddies yet).
- Navigate to Home (instructions).

2) Home (instructions) → do_home_pressed.png
- Content: “IMPORTANT BEFORE YOU RESET” + short 5-step DO instructions.
- Two primary actions:
  A) Back up to Google Drive
  B) Enable Device Owner

3A) Backup path
- onTapBackup → show do_google_backup_unclicked.png.
- onBackupConfirm → show do_google_backup_clicked.png (success/pressed state).
- After backup completes, user can Return Home → do_home_pressed.png.

3B) Enable DO (factory reset)
- From do_home_pressed.png, tap “ENABLE DEVICE OWNER”.
- App triggers the factory reset flow (DO enrollment flow prepared; QR email sent).
- Immediately send the DO provisioning email (contents below).
- Device resets.

---

## DO Provisioning Email (send when user taps “Enable Device Owner”)

Subject: Your Wype Setup – Scan this QR to enable Device Owner

Body (plain text or HTML):

Hi there,

Thanks for purchasing Wype.

To activate voice-triggered factory reset, Wype needs to become the Device Owner on your device.

IMPORTANT – do these steps after your phone resets:
1. Connect to Wi-Fi
2. When Android asks “Copy apps & data?” — tap Don’t Copy
3. When you’re asked to scan a QR code — scan the Wype QR below
4. Wype will auto-install as Device Owner
5. Once setup is complete you can restore your Google backup normally

Wype never stores any of your personal data.

Please keep this email safe so you can access the QR when needed.

Kind regards,
Wype Team

- Include the Wype DO QR (a universal enrollment QR is acceptable).
- Backend records timestamp + device token issuance (if you use tokenized QR).

---

## After Reset — Android Setup Wizard (critical)

- User reaches Android Setup Wizard.
- Must choose “Don’t copy” when asked “Copy apps & data?”
- When prompted to scan a QR, they scan the Wype DO QR from the email.
- Android provisions Wype as Device Owner (DPC installs silently).
- Wype launches in DO context.

---

## First Launch in DO mode (re-identify)

- User signs in with phone (SMS OTP) or email (magic link/OTP).
- Backend looks up userId by verified phone/email.
- If this is first-ever install, profile is empty—no phrases/buddies yet.
- App routes to Home (instructions) and normal navigation resumes (original screens keep working).

---

## Screen Routing Summary (state machine)

- onAppFirstRun → do_register.png
- onRegisterSubmit(success) → do_home_pressed.png

From do_home_pressed.png:
- onTapBackup → do_google_backup_unclicked.png
- onBackupConfirm → do_google_backup_clicked.png → onBack → do_home_pressed.png

- onTapEnableDO:
  - send DO email (above)
  - start factory reset (provisioning QR is in email)

Post-reset:
- Android Setup Wizard → Don’t copy → Scan QR → Wype becomes Device Owner.
- Wype auto-launch → onReidentify (phone/email OTP) → do_home_pressed.png (+ rest of original app screens/features)

---

## Critical Constraints

- DO must be applied before restoring Google backup. During Setup Wizard choose “Don’t copy”.
- Identity is server-side (no local data survives reset). After DO, user re-verifies and the profile is fetched (even if empty).
- Universal QR is allowed: keep provisioning identity separate from DO enrollment; re-identification happens post-provisioning.
- Non-destructive for scaffolding: do not trigger factory reset or device-policy actions in development builds yet.

---

## Assets naming note
If actual file names differ (e.g., do_home-pressed_pressed.png instead of do_home_pressed.png), record the mapping below and standardize references in code later without removing existing assets yet.

- do_home_pressed.png ↔ do_home-pressed_pressed.png
- do_google_backup_unclicked.png ↔ google_backup_unclicked.png (if present)
- do_google_backup_clicked.png ↔ google_backup_clicked.png (if present)


### Assets verification (2025-11-04T15:09:42)
- do_register.png => register.png
- do_google_backup_unclicked.png => google_backup_unclicked.png
- do_home_pressed.png => home_clicked.png
- do_google_backup_clicked.png => google_backup_clicked.png
- register_clicked.png OK

