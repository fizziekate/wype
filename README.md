# WypeFactoryReset_REGISTER_FIRST

README for local project path:
`C:\Users\Felicity\WypeFactoryReset_REGISTER_FIRST`

## Overview

This project is a Gradle-based scaffold for the **WypeFactoryReset_REGISTER_FIRST** workflow.
It is intended to be the starting point for a "register first" factory-reset flow and can be extended with your application modules, source code, and deployment logic.

## Current Status

- Gradle wrapper is configured.
- Root project name is set to `Wype`.
- Java source and test structure is in place under `src/main` and `src/test`.
- A runnable register-first entry point is available.
- CI is configured to run build and test checks.

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

## Run the Register-First Demo

Use the custom Gradle task:

- Windows:
  ```powershell
  .\gradlew.bat registerFirstDemo
  ```
- macOS/Linux:
  ```bash
  ./gradlew registerFirstDemo
  ```

You can also run with custom arguments:

- Windows:
  ```powershell
  .\gradlew.bat run --args="--user=Felicity --device=PIXEL-8-PRO"
  ```
- macOS/Linux:
  ```bash
  ./gradlew run --args="--user=Felicity --device=PIXEL-8-PRO"
  ```

## Project Structure

```text
WypeFactoryReset_REGISTER_FIRST/
├─ .github/
│  └─ workflows/
│     └─ ci.yml
├─ src/
│  ├─ main/
│  │  └─ java/com/wype/registerfirst/
│  └─ test/
│     └─ java/com/wype/registerfirst/
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle.properties
├─ gradle/
│  └─ wrapper/
├─ gradlew
├─ gradlew.bat
├─ LICENSE
└─ README.md
```

## Implemented in this repository

1. Source code added under standard Gradle directories (`src/main` and `src/test`).
2. Required dependencies added to `build.gradle.kts` (JUnit 5 for testing).
3. "Register first" flow implemented as an application entry point and a `registerFirstDemo` Gradle task.
4. CI checks added via GitHub Actions (`.github/workflows/ci.yml`) to run build + test.

## Troubleshooting

- If Gradle fails because Java is missing, verify:

  ```powershell
  java -version
  ```

- If wrapper files are blocked on Windows, run PowerShell as Administrator and retry.

## License

This project is licensed under the **MIT License**.
See the [LICENSE](LICENSE) file for details.