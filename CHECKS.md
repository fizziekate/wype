Acceptance Checks for WypeApp DO Scaffolding

Context
- [ ] Shell is in C:\Users\Felicity\WypeApp (`Get-Location` prints this path)

Git
- [ ] Git repository initialized with initial commit on main

Docs
- [ ] docs\DO_Flow.md present with routing, DO email content, constraints
- [ ] docs\state_machine.txt present
- [ ] DO_Flow.md contains assets verification/mapping section

Templates
- [ ] templates\do_provisioning_email.html and .txt exist and include QR placeholder

Scripts
- [ ] scripts\generate_wype_do_qr.ps1 present with placeholder JSON support
- [ ] scripts\provisioning.json present
- [ ] scripts\README.txt explains how to generate QR PNG

Android Stubs
- [ ] app\src\main\java\com\wype\security\deviceowner\*.kt present (navigator, email service, provisioning spec, backup coordinator, activity)
- [ ] No factory reset or DevicePolicyManager actions are invoked yet

Backend (optional but recommended)
- [ ] backend\ skeleton exists with package.json, tsconfig.json, src\index.ts, .env.example

Assets
- [ ] Key PNGs exist or are mapped (see DO_Flow.md)
