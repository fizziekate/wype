# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Common commands (Gradle wrapper)
Use the Gradle wrapper in this repo. The wrapper is configured for Gradle 8.9 (see `gradle/wrapper/gradle-wrapper.properties`). Normal development should use the wrapper (not the vendored `.gradle-dist/gradle-8.9` directory).

- List all available tasks
  - Unix/macOS:
    ```sh
    ./gradlew tasks --all
    ```
  - Windows:
    ```powershell
    .\gradlew.bat tasks --all
    ```

- Show subprojects (none today)
  - Unix/macOS:
    ```sh
    ./gradlew projects
    ```
  - Windows:
    ```powershell
    .\gradlew.bat projects
    ```

- Show project properties
  - Unix/macOS:
    ```sh
    ./gradlew properties
    ```
  - Windows:
    ```powershell
    .\gradlew.bat properties
    ```

- Inspect declared dependencies (none yet)
  - Unix/macOS:
    ```sh
    ./gradlew dependencies
    ```
  - Windows:
    ```powershell
    .\gradlew.bat dependencies
    ```

- Inspect buildscript classpath
  - Unix/macOS:
    ```sh
    ./gradlew buildEnvironment
    ```
  - Windows:
    ```powershell
    .\gradlew.bat buildEnvironment
    ```

- Show detected JDK toolchains
  - Unix/macOS:
    ```sh
    ./gradlew javaToolchains
    ```
  - Windows:
    ```powershell
    .\gradlew.bat javaToolchains
    ```

- Manage the Gradle wrapper (configured for 8.9)
  - Unix/macOS:
    ```sh
    ./gradlew wrapper
    ```
  - Windows:
    ```powershell
    .\gradlew.bat wrapper
    ```

- Build / lint / test
  - Not configured yet. These tasks are absent because no language plugins or source sets are applied in `build.gradle.kts`.

## High-level architecture and structure
- Single Gradle root project named "Wype" using Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`).
- No subprojects included and no language/plugins applied yet (so only help/inspection tasks are available).
- Gradle wrapper targets Gradle 8.9 (`gradle/wrapper/gradle-wrapper.properties`). Always invoke via `./gradlew` (Unix) or `./gradlew.bat` (Windows).
- Version catalog exists at `gradle/libs.versions.toml` and is currently empty.
- `gradle.properties` enables parallel builds and build caching.
- A vendored Gradle distribution exists under `.gradle-dist/gradle-8.9`; it is not used by the wrapper.

## Repo docs and rules
- This is the initial WARP.md for this repository.
- No repository `README.md` found.
- No CLAUDE rules (`CLAUDE.md`), Cursor rules (`.cursor/rules/` or `.cursorrules`), or Copilot rules (`.github/copilot-instructions.md`) were found to summarize.
