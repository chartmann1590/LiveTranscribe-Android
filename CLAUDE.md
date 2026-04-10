# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**LiveCaptionN** — an Android app (Kotlin) that captures speech via microphone, transcribes it in real time using Android's `SpeechRecognizer`, translates between English and Vietnamese via a LibreTranslate-compatible HTTP server, and displays captions as a draggable floating overlay (`SYSTEM_ALERT_WINDOW`) on top of other apps.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single test class
./gradlew testDebugUnitTest --tests "com.charles.livecaptionn.SomeTest"

# Run instrumentation tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

Requires JDK 17. Target/compile SDK 35, min SDK 29.

## Architecture

MVVM with manual dependency injection. No Hilt/Dagger — dependencies are wired through `AppContainer` (created in `LiveCaptionApp`, the `Application` subclass).

### Data Flow

1. **Speech** → `AndroidSpeechRecognizerManager` wraps Android `SpeechRecognizer`, emits recognized text via callback
2. **Service** → `CaptionForegroundService` orchestrates everything: receives speech results, debounces translation requests (400ms), updates runtime state
3. **Translation** → `LibreTranslateRepository` calls a configurable LibreTranslate HTTP endpoint via Retrofit. Falls back to showing original text on failure
4. **Overlay** → `OverlayController` manages a `WindowManager`-based floating view (Android Views, not Compose). Draggable with position persisted to DataStore
5. **UI** → `MainScreen` (Jetpack Compose) with `MainViewModel` for the settings/control screen

### Key Interfaces

- `TranslationRepository` — translation abstraction with `LibreTranslateRepository` (real) and `MockTranslationRepository` (testing)
- `SettingsRepository` → `SettingsDataStore` — wraps DataStore Preferences for all persisted settings
- `SpeechEngine` — speech recognition abstraction
- `CaptionRuntimeStore` — in-memory `StateFlow` holding live caption state (original text, translated text, status)

### State Management

- **Persistent settings** (`CaptionSettings`): language pair, text size, overlay opacity/position, show-original toggle, translation base URL — stored in DataStore via `SettingsRepository`
- **Runtime state** (`CaptionRuntimeState`): running/paused flags, current transcript, translated text, recognition status — held in `CaptionRuntimeStore` (in-memory `MutableStateFlow`)

### Overlay

The floating overlay uses traditional Android Views (not Compose) because `WindowManager` overlays require direct view manipulation. It is separate from the Compose-based main settings screen.

## Translation API

Targets a LibreTranslate-compatible server. Default endpoint is configurable in-app. Two endpoints used:
- `GET /languages` — list available languages
- `POST /translate` — body: `{q, source, target, format:"text"}`

## Dependencies

- **UI**: Jetpack Compose + Material 3 (main screen), Android Views (overlay)
- **Networking**: Retrofit + OkHttp + Moshi
- **Persistence**: DataStore Preferences
- **Kotlin**: Coroutines + Flow
