# Repository Guidelines

## Project Structure & Module Organization

LiveCaptionN is a single-module Android project. The root Gradle files (`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`) configure the build, and the Android app lives in `app/`. Production Kotlin code is under `app/src/main/java/com/charles/livecaptionn/`, grouped by role:

- `ui/` for Jetpack Compose screens and view models.
- `service/`, `speech/`, and `overlay/` for caption runtime behavior.
- `translation/`, `data/`, `settings/`, and `di/` for repositories, models, persistence, and manual dependency wiring.

Resources are in `app/src/main/res/`. Unit tests are in `app/src/test/java/com/charles/livecaptionn/`. CI configuration is in `.github/workflows/build.yml`.

## Build, Test, and Development Commands

Use the Gradle wrapper with JDK 17.

- `./gradlew assembleDebug` builds a debug APK.
- `./gradlew assembleRelease` builds an unsigned release APK.
- `./gradlew testDebugUnitTest` runs local unit tests for the debug variant.
- `./gradlew test` runs all local unit tests.
- `./gradlew connectedAndroidTest` runs instrumentation tests on a connected device or emulator.
- `./gradlew clean` removes generated build output.

On Windows PowerShell, use `.\gradlew.bat` instead of `./gradlew`.

## Coding Style & Naming Conventions

Write Kotlin using four-space indentation and idiomatic Android/Kotlin naming: `PascalCase` for classes and data classes, `camelCase` for functions and properties, and descriptive test names where practical. Keep Compose UI in `ui/`; do not mix overlay `WindowManager` view code into Compose screens. Dependencies are wired manually through `AppContainer`; do not introduce a DI framework without a deliberate architecture change.

## Testing Guidelines

The project uses JUnit 4 for local unit tests and AndroidX/JUnit/Espresso/Compose tooling for instrumentation tests. Name test files after the class or behavior under test, such as `CaptionRuntimeStoreTest.kt` or `WavEncoderTest.kt`. Add or update tests for repository logic, settings persistence, runtime state, and pure Kotlin utilities when behavior changes.

## Commit & Pull Request Guidelines

Recent commits use concise, imperative summaries, for example `Fix release workflow: add contents write permission` and `Initial commit: LiveCaptionN Android MVP`. Keep commits focused and mention the affected area when useful. Pull requests should include a short description, test results such as `./gradlew testDebugUnitTest`, linked issues when applicable, and screenshots or recordings for visible UI or overlay changes.

## Security & Configuration Tips

Do not commit secrets, keystores, APK/AAB outputs, or machine-specific files. `local.properties` is ignored and may contain private `translate.url` or `stt.url` values; defaults are provided by `app/build.gradle.kts` for local development.
