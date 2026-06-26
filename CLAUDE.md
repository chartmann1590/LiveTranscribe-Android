# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**LiveCaptionN** (`com.charles.livecaptionn`) — an Android app (Kotlin) that captures speech from the **microphone or system audio** (`MediaProjection`), transcribes it in real time, translates between language pairs, and renders captions in a draggable `SYSTEM_ALERT_WINDOW` overlay on top of any other app.

Both stages of the pipeline are designed to run **fully on-device by default**: streaming Vosk for STT, Google ML Kit for translation. Remote backends (LibreTranslate, Whisper HTTP) are still selectable as fallbacks.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (signs only if keystore.properties exists at repo root)
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

Requires JDK 17. `compileSdk`/`targetSdk` 35, `minSdk` 29. APKs land in `app/build/outputs/apk/`.

### Build-time configuration

- `local.properties` (gitignored) supplies dev-time defaults via `BuildConfig`:
  - `translate.url` → `BuildConfig.DEFAULT_TRANSLATE_URL` (fallback `http://localhost:3006`)
  - `stt.url` → `BuildConfig.DEFAULT_STT_URL` (fallback `http://localhost:9000/asr?output=json`)
- `keystore.properties` (gitignored, template at `playstore/keystore.properties.example`) enables release signing. Without it, `assembleRelease` produces an unsigned APK.
- CI builds derive `versionCode`/`versionName` from `GITHUB_RUN_NUMBER` (local builds get `1`).
- `BuildConfig.UPDATE_REPO_OWNER`/`UPDATE_REPO_NAME` point the in-app update checker at the GitHub releases API.

## Architecture

MVVM with manual dependency injection. No Hilt/Dagger — every dependency is wired through `AppContainer` (created once in `LiveCaptionApp`, the `Application` subclass).

### Data flow

1. **Audio** — one `AudioRecord` reads 16 kHz mono PCM in ~100 ms chunks from either the microphone (`VOICE_RECOGNITION`) or `MediaProjection`'s `AudioPlaybackCaptureConfiguration`. `MediaProjectionHolder` stashes the projection result intent across the activity → service handoff.
2. **STT** — chunks are fed straight into a single long-lived Vosk `Recognizer` (`VoskStreamingSession`), which emits `partial` results every chunk and `final` segments on silence boundaries. `StreamingSttEngine` is the default and handles both mic and system audio.
3. **Service** — `CaptionForegroundService` orchestrates everything: chooses speech engine + foreground-service type, owns the translate-debounce flow, and writes runtime state.
4. **Translation** — speech results are coalesced via a `MutableSharedFlow` + `.debounce(450 ms)` (mic partials arrive faster than that; without this they'd cancel each other). `RoutingTranslationRepository` re-reads `settings.translationBackend` per call and dispatches to `MlKitTranslationRepository` or `LibreTranslateRepository`. **An empty translation result is treated as failure** and preserves the last good translation (do not overwrite with raw source).
5. **State** — `CaptionRuntimeStore` holds a `MutableStateFlow<CaptionRuntimeState>` (transcript lines, originalText, translatedText, status, lastError).
6. **Overlay** — `OverlayController` drives a `WindowManager` view from a `combine(runtimeStore, settingsFlow, overlayReady)` stream, gated by `collectLatest`. The overlay-ready signal exists because `combine` was skipping while the view was still null on startup.
7. **History** — finals (`isFinal=true`) flag `historyOnNextTranslate`, so when the next translation completes the pair is appended to `TranscriptHistoryStore` for the History screen.

### Key abstractions

- `SpeechEngine` — implementations:
  - `StreamingSttEngine` — default. Streaming Vosk for both mic and system audio.
  - `AndroidSpeechRecognizerManager` — Google's on-device recognizer (mic only). Used when `sttBackend = REMOTE_WHISPER` + `audioSource = MIC`.
  - `SystemAudioEngine` — legacy batch path that POSTs WAVs to a remote Whisper endpoint. Used when `sttBackend = REMOTE_WHISPER` + `audioSource = SYSTEM`.
- `TranslationRepository` — implementations:
  - `MlKitTranslationRepository` — on-device, ~30 MB per language pair, lazily downloaded.
  - `LibreTranslateRepository` — Retrofit client for a configurable HTTP server.
  - `RoutingTranslationRepository` — picks one per call based on settings.
  - `MockTranslationRepository` — for tests.
  - All implement `prewarm(source, target)` — the service prewarms on session start so the first utterance doesn't stall on a model download.
- `SettingsRepository` → `SettingsDataStore` — DataStore Preferences for `CaptionSettings`.
- `LanguageCatalogStore` — lazy-loads ML Kit's static list, LibreTranslate's `/languages`, or installed Vosk models depending on selected backends.
- `VoskModelRegistry` — manages bundled + downloaded Vosk models on disk; `LocalVoskSttClient` is the runtime entry point.
- `UpdateChecker` + `UpdateCheckWorker` + `UpdateNotifier` — periodic WorkManager job (12 h, network-required, KEEP policy) polls the GitHub Releases API, compares against `BuildConfig.VERSION_CODE`, and posts a notification with a one-tap Download action.
- `AppOpenAdManager` + `BannerAd` — AdMob integration. App-open ad is wired through `ProcessLifecycleOwner` to detect foreground entries.

### Settings model

`CaptionSettings` (DataStore-persisted) carries: `sourceLanguageCode`, `targetLanguageCode`, `autoDetectSource`, `audioSource` (MIC/SYSTEM), `sttBackend` (LOCAL_VOSK/REMOTE_WHISPER), `translationBackend` (ML_KIT/LIBRE_TRANSLATE), `serverBaseUrl`, `sttBaseUrl`, plus overlay tuning (`textSizeSp`, `overlayOpacity`, `overlayX/Y`, `overlayWidthDp/HeightDp`, `overlayMinimized`, `showOriginal`).

`CaptionRuntimeState` is purely in-memory (running, paused, transcript lines, current original/translated text, status, lastError) and resets on stop.

### Engine routing in the service

```
sttBackend = LOCAL_VOSK  → StreamingSttEngine (mic OR system audio)
sttBackend = REMOTE_WHISPER + audioSource = SYSTEM → SystemAudioEngine (batch WAV → Whisper)
sttBackend = REMOTE_WHISPER + audioSource = MIC    → AndroidSpeechRecognizerManager
```

Foreground service type is `MEDIA_PROJECTION` for system audio and `MICROPHONE` otherwise — picked at `startForeground` time based on `audioSource`.

### Overlay specifics

The overlay uses traditional Android Views (not Compose) because `WindowManager`-managed overlays don't play well with Compose's lifecycle. It is fully separate from the Compose-based settings/history UI.

The overlay body only renders the **translated** stream — partial lines without a translation yet are kept in `transcriptLines` (for the history screen) but suppressed in the overlay so users don't see source + target stacked.

## Translation API (LibreTranslate)

Default endpoint configurable in-app, default sourced from `BuildConfig.DEFAULT_TRANSLATE_URL`. Endpoints:
- `GET /languages` — used by `LanguageCatalogStore` to populate pickers
- `POST /translate` — body: `{q, source, target, format:"text"}`

## Dependencies (notable)

- **UI**: Jetpack Compose + Material 3 (settings/history), Android Views (overlay)
- **Networking**: Retrofit + OkHttp + Moshi
- **Persistence**: DataStore Preferences
- **STT**: `com.alphacephei:vosk-android` (streaming on-device)
- **Translation**: `com.google.mlkit:translate` (on-device)
- **Background**: WorkManager (update checks)
- **Ads**: `play-services-ads` (banner + app-open)
- **Kotlin**: Coroutines + Flow
