<div align="center">

# LiveCaptionN

**Real-time speech transcription and EN ⇄ VI translation, floating over any Android app.**

[![Build & Release](https://github.com/chartmann1590/LiveTranscribe-Android/actions/workflows/build.yml/badge.svg)](https://github.com/chartmann1590/LiveTranscribe-Android/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/chartmann1590/LiveTranscribe-Android?color=8b5cf6)](https://github.com/chartmann1590/LiveTranscribe-Android/releases/latest)
[![License](https://img.shields.io/github/license/chartmann1590/LiveTranscribe-Android?color=6366f1)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%2010%2B-1a1330)](#requirements)

### [Website](https://chartmann1590.github.io/LiveTranscribe-Android/) · [Download APK](https://github.com/chartmann1590/LiveTranscribe-Android/releases/latest) · [Report an issue](https://github.com/chartmann1590/LiveTranscribe-Android/issues)

</div>

---

LiveCaptionN listens through the microphone (or the currently playing app audio), transcribes what it hears in near real time, translates between English and Vietnamese, and paints the result as a draggable caption window on top of whatever you are watching or browsing. It is built for people watching foreign-language videos, following along in a meeting, or studying another language hands-free.

## Screenshots

<div align="center">

<img src="docs/assets/screenshots/overlay_home.png" alt="Floating caption overlay on the home screen" width="240" />&nbsp;&nbsp;
<img src="docs/assets/screenshots/overlay_listening.png" alt="Overlay captioning while listening" width="240" />&nbsp;&nbsp;
<img src="docs/assets/screenshots/main.png" alt="Main settings screen" width="240" />&nbsp;&nbsp;
<img src="docs/assets/screenshots/history.png" alt="Transcript history" width="240" />

</div>

## Features

- **Floating caption overlay** — draggable, resizable `SYSTEM_ALERT_WINDOW` window that sits on top of any app, with Pause, Minimize, and Close controls.
- **Multiple speech engines** — Android's built-in `SpeechRecognizer`, a Whisper HTTP endpoint, or fully offline **Vosk** models.
- **Mic or system audio** — switch between the microphone and `MediaProjection` audio capture of the currently playing app.
- **EN ⇄ VI translation** — pluggable LibreTranslate-compatible HTTP backend. Auto-detect source, show original alongside translation, or hide it.
- **Transcript history** — every session is saved locally and searchable from the history screen.
- **Tunable overlay** — text size, opacity, width/height, "show original" toggle, minimized state, and remembered screen position.
- **Private by default** — speech processing and translation both run against endpoints you configure. No accounts, no telemetry.

## Quick install

1. Download the latest APK from the [releases page](https://github.com/chartmann1590/LiveTranscribe-Android/releases/latest).
2. On your Android device, enable **Install unknown apps** for your browser / file manager if prompted.
3. Open the APK and install.
4. Launch LiveCaptionN and grant **Microphone** and **Display over other apps** permissions.
5. (Optional) Point the Translation base URL at your own LibreTranslate server.
6. Tap **Start Captioning**, then switch to any app you want to watch or listen to.

> Minimum Android version: **Android 10 (API 29)**. Target: **Android 15 (API 35)**.

## How it works

```
┌─────────────┐    ┌──────────────────┐    ┌────────────────┐    ┌──────────────┐
│ Mic / Media │ ─▶ │ Speech engine    │ ─▶ │ Translation    │ ─▶ │ Floating     │
│ Projection  │    │ (Android / Vosk  │    │ (LibreTranslate│    │ overlay on   │
│ audio       │    │  / Whisper HTTP) │    │  HTTP server)  │    │ other apps   │
└─────────────┘    └──────────────────┘    └────────────────┘    └──────────────┘
```

A foreground `CaptionForegroundService` wires everything together, debounces translation requests (~400 ms), and pushes updates into a `StateFlow` that both the Compose main screen and the Android-Views overlay observe.

## Architecture at a glance

- **MVVM with manual DI** — all dependencies wired through `AppContainer` (created in `LiveCaptionApp`). No Hilt.
- **`TranslationRepository`** — abstraction with `LibreTranslateRepository` (Retrofit) and `MockTranslationRepository` for tests.
- **`SpeechEngine`** — abstraction over Android `SpeechRecognizer`, Whisper HTTP, and on-device Vosk.
- **`CaptionRuntimeStore`** — in-memory `MutableStateFlow` holding live caption state.
- **`SettingsRepository`** — DataStore Preferences persistence for every user-visible setting plus overlay position.
- **Overlay** — traditional Android Views via `WindowManager` (Compose does not play well with `SYSTEM_ALERT_WINDOW`).

For deeper notes see [`CLAUDE.md`](CLAUDE.md).

## Translation backend

LiveCaptionN talks to any LibreTranslate-compatible server. The default endpoint is `http://localhost:3006` and is configurable in the main screen.

- `GET /languages` — list supported languages
- `POST /translate` — body: `{ q, source, target, format: "text" }`

You can self-host LibreTranslate with Docker in a few minutes — see the [LibreTranslate project](https://github.com/LibreTranslate/LibreTranslate).

## Build from source

Requires **JDK 17** and the Android SDK. Tested with Android Studio Hedgehog+.

```bash
# Clone
git clone https://github.com/chartmann1590/LiveTranscribe-Android.git
cd LiveTranscribe-Android

# Debug APK
./gradlew assembleDebug

# Release APK (unsigned)
./gradlew assembleRelease

# Unit tests
./gradlew test

# Instrumentation tests (connected device required)
./gradlew connectedAndroidTest
```

Output APKs land in `app/build/outputs/apk/`.

## Requirements

| Item | Value |
|---|---|
| Min SDK | 29 (Android 10) |
| Target / Compile SDK | 35 (Android 15) |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 (main), Android Views (overlay) |
| Build tool | Gradle 8 + AGP |

## Tech stack

Kotlin · Coroutines & Flow · Jetpack Compose · Material 3 · Retrofit · OkHttp · Moshi · DataStore Preferences · Vosk · WindowManager · MediaProjection · Foreground Service

## Roadmap

- Additional language pairs beyond EN ⇄ VI
- On-device translation models
- Accessibility-service experiments for richer in-app context
- Per-app overlay profiles

## Contributing

Issues and pull requests are welcome. Before opening a PR:

1. Run `./gradlew test` and make sure it passes.
2. Keep changes focused — one concern per PR.
3. If you change overlay rendering, include a screenshot.

## License

See [`LICENSE`](LICENSE).
