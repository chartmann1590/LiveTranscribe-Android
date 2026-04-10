# Live CaptionN (Android MVP)

An Android MVP app in Kotlin that captures speech from the microphone, transcribes it in near real time, translates EN<->VI with a LibreTranslate-compatible server, and shows captions as a floating overlay above other apps.

## What this MVP does
- Foreground service for continuous captioning.
- Draggable floating overlay (`SYSTEM_ALERT_WINDOW`) with:
  - Live transcript
  - Translated text
  - Status (Listening/Processing/Paused/Error)
  - Pause/Resume, Minimize/Expand, Close controls
- Main screen with:
  - Start/Stop buttons
  - Source/target language selectors (English/Vietnamese)
  - Auto-detect source toggle
  - Text size slider
  - Overlay opacity slider
  - Show-original toggle
  - Translation base URL field
  - Permission status indicators
- DataStore persistence for all key settings and overlay position/state.

## Default translation endpoint
- `http://localhost:3006`
- Configurable in app UI settings.

## Tech stack
- Kotlin + Android SDK
- Jetpack Compose UI
- MVVM
- Foreground Service
- `SpeechRecognizer` for microphone speech-to-text
- Retrofit + OkHttp for translation API
- DataStore Preferences for persistence

## Build and run
1. Open project in Android Studio (Hedgehog+ recommended).
2. Let Gradle sync and download dependencies.
3. Run on a physical Android device (API 29+).
4. Grant:
   - Microphone permission
   - Overlay permission
5. Tap **Start Captioning**, then open YouTube/TikTok/browser/etc.

## LibreTranslate API behavior
- `GET /languages` supported by API interface.
- `POST /translate` request body:
  - `q` text
  - `source` (`en`, `vi`, or `auto`)
  - `target` (`en` or `vi`)
  - `format = text`

## Android limitations (important)
- This MVP intentionally uses **microphone input**.
- It does **not** claim reliable direct internal audio capture from other apps.
- Internal audio capture on Android depends on OS/app policy and is often restricted for third-party apps.
- For this reason, the practical MVP path is live microphone captioning over apps/videos.

## Future upgrade paths
- Offline/on-device speech recognition engine.
- Offline/on-device translation models.
- MediaProjection and advanced audio-capture experiments.
- Accessibility-based enhancements for richer context.

## Debug notes
- Service, speech, and translation failures are logged with Android `Log`.
- If translation fails, app falls back to showing original transcript text.
