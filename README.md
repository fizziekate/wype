# WypeFactoryReset_REGISTER_FIRST

README for local project path:
`C:\Users\Felicity\WypeFactoryReset_REGISTER_FIRST`

## Overview

This project is a Gradle-based scaffold for the **WypeFactoryReset_REGISTER_FIRST** workflow.
It is intended to be the starting point for a "register first" factory-reset flow and can be extended with your application modules, source code, and deployment logic.

## Current Status

- Gradle wrapper is configured.
- Root project name is set to `Wype`.
- No application modules or source sets are committed yet.

## Prerequisites

- Windows 10/11 (or macOS/Linux)
- Java Development Kit (JDK) 17+ installed
- Git (optional, for version control)

## Quick Start (Windows)

Open PowerShell in:

```powershell
cd C:\Users\Felicity\WypeFactoryReset_REGISTER_FIRST
```

Then run:

```powershell
.\gradlew.bat tasks
.\gradlew.bat build
.\gradlew.bat test
```

## Quick Start (macOS/Linux)

```bash
./gradlew tasks
./gradlew build
./gradlew test
```

## Project Structure

```text
WypeFactoryReset_REGISTER_FIRST/
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle.properties
├─ gradle/
│  └─ wrapper/
├─ gradlew
├─ gradlew.bat
└─ README.md
```

## Recommended Next Steps

1. Add source code under standard Gradle directories (for example, `src/main` and `src/test`).
2. Add required dependencies to `build.gradle.kts`.
3. Define your "register first" flow as runnable tasks or an application entry point.
4. Add CI checks (build + test) once core logic is in place.

## Troubleshooting

- If Gradle fails because Java is missing, verify:

  ```powershell
  java -version
  ```

- If wrapper files are blocked on Windows, run PowerShell as Administrator and retry.

## License

This project is licensed under the **MIT License**.
See the [LICENSE](LICENSE) file for details.