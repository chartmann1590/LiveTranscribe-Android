Build me a complete Android MVP app in Kotlin using Android Studio that does live speech captioning and translation as a floating overlay on top of other apps, including video apps like TikTok, YouTube, Facebook, browser video, and similar apps.

App goal:
Create an Android app similar in spirit to Google Translate live caption behavior, where the user can grant microphone permission and overlay permission, and the app listens to spoken audio, converts it to text in near real time, translates it, and displays the translated captions on screen over videos and other apps.

Important requirements:
- Use free APIs or free/open-source solutions only
- MVP-first: simple, working, clean, and expandable
- Must be realistic about Android limitations
- For MVP, use microphone-based capture instead of pretending full internal audio capture works everywhere
- Must support Vietnamese and English
- Must display translated text as a floating overlay above other apps
- Must be production-minded enough that I can improve it later

My translation server:
- Use this LibreTranslate-compatible endpoint by default:
  http://localhost:3006
- Make the translation base URL configurable in app settings
- Default language pair should be:
  - Source: English
  - Target: Vietnamese
- Also support switching from Vietnamese to English

Tech stack:
- Kotlin
- Android SDK
- Jetpack Compose for the app UI if practical, otherwise XML is acceptable
- MVVM architecture
- Foreground service for continuous listening
- Overlay window using SYSTEM_ALERT_WINDOW permission
- Android SpeechRecognizer for MVP speech-to-text unless a better free on-device approach is practical
- Retrofit + OkHttp for translation API calls
- DataStore for settings persistence
- Clear comments throughout the code

Core MVP features:

1. Main screen
- Start Captioning button
- Stop Captioning button
- Source language selector
- Target language selector
- Toggle for auto-detect source language if supported
- Text size slider
- Overlay opacity slider
- Toggle to show original text above translated text
- Permission status indicators:
  - microphone permission
  - overlay permission
- Translation server URL field prefilled with:
  http://localhost:3006

2. Overlay behavior
- Floating movable overlay panel
- User can drag it anywhere
- Overlay stays above other apps and videos
- Overlay can be minimized and expanded
- Overlay displays:
  - current live transcript
  - translated text
- Text updates continuously as speech is recognized
- Styling should be readable on top of video:
  - semi-transparent dark background
  - rounded corners
  - high contrast text
  - multi-line captions
  - minimal flicker during updates
- Include simple controls in the overlay:
  - pause/resume
  - close overlay
  - minimize/expand

3. Speech pipeline
- Capture speech from microphone continuously
- Transcribe in chunks or near-real-time
- Translate recognized text
- Display translated text in overlay
- Handle pauses gracefully
- Automatically restart recognition if Android SpeechRecognizer stops listening
- Show status such as:
  - Listening
  - Processing
  - Paused
  - Error

4. Translation layer
- Create a TranslationRepository interface so translation providers can be swapped later
- Default implementation must call the LibreTranslate-compatible server at:
  http://localhost:3006
- Add a fallback/mock translator for testing if the server is unavailable
- Translation service should support:
  - English to Vietnamese
  - Vietnamese to English
- Parse LibreTranslate-style request/response cleanly
- Handle timeouts and network errors gracefully

5. Settings persistence
Save and restore:
- source language
- target language
- overlay position
- overlay text size
- overlay opacity
- whether original text is shown
- translation server base URL
- minimized/expanded overlay state if practical

6. Permissions and lifecycle
- Request RECORD_AUDIO permission
- Guide the user to enable overlay permission
- Use a foreground service so Android does not kill the app immediately
- Show a persistent notification while captioning is active
- Handle app backgrounding correctly
- Overlay should keep working while another app is in front

7. Error handling
- If speech recognition fails, show a status message in the overlay
- If translation fails, show original transcript instead of blank text
- If permissions are missing, explain exactly what the user must enable
- Log important lifecycle and error events for debugging
- Fail gracefully instead of crashing

8. App structure
Organize packages cleanly, for example:
- ui
- overlay
- service
- speech
- translation
- data
- settings

Use:
- interfaces where appropriate
- repository pattern
- modular classes
- maintainable code

Nice-to-have if easy
- Language preset buttons:
  - English → Vietnamese
  - Vietnamese → English
- Copy current caption text
- Caption history screen
- Debounce translation requests so it does not spam the API on every tiny partial update
- Simple smoothing of transcript updates

Very important implementation notes
- Do not fake features that Android does not reliably support
- In the README, clearly explain that the MVP uses microphone capture because internal app audio capture is restricted on Android depending on app and OS behavior
- Build the best practical working MVP
- Make it easy to later upgrade to:
  - offline speech recognition
  - offline translation
  - MediaProjection / advanced audio capture experiments
  - accessibility-based enhancements

LibreTranslate API integration details
Assume a LibreTranslate-compatible API hosted at:
http://localhost:3006

Implement the translation client so it can call endpoints like:
- GET /languages
- POST /translate

Expected translate request body should follow LibreTranslate-style format, for example:
{
  "q": "Hello, how are you?",
  "source": "en",
  "target": "vi",
  "format": "text"
}

Handle LibreTranslate-compatible responses and show translatedText in the overlay.

Deliverables
- Full Android Studio project
- Gradle files
- AndroidManifest.xml
- All source files
- Foreground service
- Overlay manager
- Speech recognition manager
- Translation API client
- Settings storage
- UI screens
- README with setup and limitations
- Complete runnable code with no missing critical files or TODO placeholders for core functionality

Also:
- Make the code complete and runnable
- Include all permissions, service declarations, and dependencies
- Keep the UI modern but simple
- Optimize for an MVP that actually works

Build a complete Android MVP app in Kotlin that provides live speech captioning and translation as a floating overlay over other apps and videos.

Requirements:
- Kotlin
- Android Studio project
- MVVM
- Foreground service
- Overlay permission
- RECORD_AUDIO permission
- Microphone-based speech capture for MVP
- Floating draggable overlay above other apps
- Live transcript + translated text
- Vietnamese and English support
- Use LibreTranslate-compatible API at:
  http://localhost:3006
- Make API URL configurable in settings
- Default to English -> Vietnamese
- Support Vietnamese -> English too
- Use Retrofit/OkHttp
- Use DataStore
- Use Android SpeechRecognizer for MVP
- Auto-restart listening when recognition stops
- Show status messages like Listening, Processing, Error
- Overlay should be readable on top of video with semi-transparent dark background and large text
- Include Start/Stop screen, settings, and permission checks
- Save overlay settings and selected languages
- If translation fails, show original text
- Include full runnable code, manifest, services, dependencies, and README
- Do not leave placeholders for core functionality
- Clearly document Android limitations around internal audio capture and explain that MVP uses microphone input

Do not just scaffold. Generate the full runnable project with complete code for every required file.