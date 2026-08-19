package com.charles.livecaptionn.ui.l10n

import java.util.Locale

/**
 * A runtime-localized string table for the whole app UI.
 *
 * Each map key is the canonical English source string; when a key has no
 * translation yet the English text is returned, so the UI degrades gracefully
 * while ML Kit downloads a model / translates in the background — the UI never
 * renders blank or half-localized text.
 */
class UiStrings(private val map: Map<String, String> = emptyMap()) {

    operator fun get(key: String): String = map[key] ?: key

    val isLocalized: Boolean get() = map.isNotEmpty()

    /** Translator for map values with an empty-translation guard. */
    fun translated(key: String): String? = map[key]

    /**
     * Localizes an English format template while preserving its placeholders.
     * Falls back to the English template if the translation is missing or
     * dropped/mangled the %-specifier (which would otherwise crash or corrupt
     * String.format output).
     */
    fun format(template: String, vararg args: Any?): String {
        val localized = map[template] ?: template
        if (localized != template &&
            UiStringCatalog.placeholderCount(localized) != UiStringCatalog.placeholderCount(template)
        ) {
            return typedFormat(template, args) ?: template
        }
        return typedFormat(localized, args) ?: typedFormat(template, args) ?: template
    }

    private fun typedFormat(template: String, args: Array<out Any?>): String? = try {
        String.format(Locale.getDefault(), template, *args)
    } catch (_: Throwable) {
        null
    }

    companion object {
        val EMPTY = UiStrings(emptyMap())
    }
}

/**
 * Single source of truth for every user-facing English string the app can
 * render. These are translated on-device by ML Kit into the chosen UI language
 * and then served through [UiStrings]; English is always the fallback.
 *
 * Keys (and format templates) themselves are the English source text, so the
 * catalog stays in lock-step with the Compose screens without a separate
 * id<->text mapping to keep maintained.
 */
object UiStringCatalog {

    /** Short display name of the app itself, used directly as a catalog key. */
    const val appName = "Live CaptionN"

    /** Every unique English string / template the UI may pass through `t[...]`. */
    val ALL: List<String> = listOf(
        // App / top bar
        appName,
        "Start",
        "Stop",
        "Ready",
        "Status",
        "Idle",
        "Listening",
        "Processing",
        "Paused",
        "Error",
        "History",

        // Permissions
        "Permissions",
        "Microphone",
        "Overlay",
        "Grant",

        // Audio source
        "Audio Source",
        "System Audio",
        "Captures audio from videos and apps that allow playback capture. If Android asks, choose Share entire screen.",
        "Uses the device microphone.",
        "Transcription engine",
        "Remote Whisper",
        "Android built-in",
        "Local Vosk",
        "Runs transcription fully on this device.",
        "language",
        "languages",
        "installed.",
        "Tip: for noticeably stronger transcription, tap Manage models and install a LARGE server-grade model for the languages you use — 80 MB to 2 GB each, fully offline after download.",
        "Manage on-device models",
        "Sends captured audio to the configured Whisper ASR endpoint.",
        "Uses Android's built-in recognizer. Available locales depend on what's installed on this device.",

        // Language pickers
        "Languages",
        "Source (spoken)",
        "No languages available",
        "Pick a language",
        "Target (translation)",
        "Swap",
        "On-device transcription: source language is limited to the Vosk models installed on this phone.",
        "Download more languages",
        "On-device translation via Google ML Kit — %d languages, fully offline after first download.",
        "Could not reach LibreTranslate at %s — showing a fallback list. Save a valid URL to load the full language set from your server.",
        "Loading languages from LibreTranslate…",
        "%d languages reported by LibreTranslate. Add more to your server by installing extra Argos Translate packages.",
        "Auto-detect source",
        "Close",
        "Choose language",
        "Search",
        "Requires Pro",
        "No languages match \"%s\".",

        // Vosk model management
        "On-device speech models",
        "Vosk models run fully offline on this phone. Downloading a model is a one-time step; uninstall any time to free storage. For the strongest transcription, pick the LARGE server-grade model for the languages you use most.",
        "Installed",
        "Large · server-grade accuracy (Pro)",
        "Full Vosk server models with the lowest error rates. Each one is 80 MB to 2 GB but runs entirely on-device after the one-time download. This is the strongest transcription option for every language.",
        "Small · fast & light",
        "Compact ~40 MB models for quick installs or low-storage phones. Accuracy is noticeably lower than the large variants.",
        "Models are fetched from alphacephei.com/vosk/models over HTTPS.",
        "Delete",
        "Get",
        "LARGE",
        "bundled",
        "Pro",

        // Overlay settings
        "Text size: %dsp",
        "Opacity: %d%%",
        "Show original text",
        "Overlay theme (Pro)",
        "Overlay font (Pro)",

        // Server / translation engine
        "Translation engine",
        "On-device (ML Kit)",
        "LibreTranslate",
        "Google ML Kit translates fully on this device — no server required. The first time you use a language pair, a ~30 MB model downloads and is then cached offline forever.",
        "LibreTranslate URL",
        "Example: %s",
        "The app fetches /languages to populate the dropdowns.",
        "Save",
        "Refresh",
        "Language fetch error: %s",
        "Speech-to-Text URL (Whisper)",
        "Save STT URL",

        // Update banner
        "Update available",
        "Download APK",
        "View release",
        "Later",
        "You installed this app from the Play Store. The GitHub build is a pre-release and may be unstable. Installing it will also disable Play Store auto-updates for this app.",

        // History
        "Transcript History",
        "No matching transcripts",
        "No transcripts yet",
        "Try a different search term.",
        "Transcripts will appear here as you use captioning.",
        "Search transcripts…",
        "Clear search",
        "Clear",
        "Copy",
        "Back",
        "Delete entry",
        "Delete this transcript entry?\n\n\"%s\"",
        "Cancel",

        // Premium
        "Upgrade",
        "Ad-Free removes all ads. Pro unlocks larger on-device speech models, more translation languages, and extra overlay themes.",
        "Go Ad-Free",
        "Ad-Free active",
        "Go Pro",
        "Pro active",
        "Manage subscription",
        "Email used at checkout",
        "Restore purchase",
        "Refresh status",

        // Feedback / support
        "Support & Feedback",
        "GitHub feedback is not configured.",
        "Report a Problem",
        "Submitted Reports",
        "Open",
        "Closed",
        "Your report will be submitted to this app's public GitHub issue tracker. Do not include passwords, private keys, medical information, financial information, or anything you do not want visible to the repository maintainers.",
        "Title / Subject *",
        "Description *",
        "Include phone/app diagnostics",
        "Diagnostics include app version, device model, Android version, storage/memory info. No personal data is collected.",
        "Name (optional)",
        "Email (optional)",
        "Image attached",
        "Change image",
        "Attach screenshot / image",
        "Configuration error.",
        "Created: %s",
        "Open in browser",
        "Comments (%d)",
        "Post a Reply",
        "Your reply",
        "Attach",
        "Send",
        "Submit",
        "Dismiss",
        "Report submitted successfully!",
        "Remove",

        // Overlay window
        "Pause captioning",
        "Minimize overlay",
        "Close overlay",
        "Resume captioning",

        // Notification / foreground service
        "Captioning in progress",
        "Listening and translating speech.",
        "Pause/Resume",
        "Live Captioning",
        "Shows when live captioning is active.",

        // Update notifier
        "LiveCaptionN update available",
        "%s is ready to install",
        "Download",
        "App updates",
        "Notifies you when a new version of LiveCaptionN is available on GitHub",

        // Interface language feature
        "Interface language",
        "Choose interface language",
        "English",
        "Machine-translated interface",
        "On-device ML Kit translation may be inaccurate.",
        "UI text is translated on-device by Google ML Kit, not by human translators, and may be inaccurate.",
        "Downloading translation model…",
        "Translating the interface…",
        "Translating the interface… (%d/%d)",
        "Translation failed. Tap to retry.",
        "Switch to English",
        "Switch",
        "Retry",
        "Current",

        // Onboarding
        "Welcome",
        "Live captions & translation on your device",
        "Choose your interface language",
        "You can change this later in Settings.",
        "Get started"
    ).distinct()

    private val PLACEHOLDER = Regex("%\\d*[_a-zA-Z%]")

    fun placeholderCount(text: String): Int = PLACEHOLDER.findAll(text).count()

    /**
     * Whether a string carries enough meaning on its own to be worth
     * translating. Skips blank text, punctuation-only tokens, and stray
     * single characters (e.g. the "sp" unit suffix), which ML Kit handles
     * poorly and which would degrade translation quality.
     */
    fun isSegmentTranslatable(segment: String): Boolean {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.length < 2) return false
        if (trimmed.all { it.isWhitespace() || !it.isLetter() }) return false
        return true
    }
}