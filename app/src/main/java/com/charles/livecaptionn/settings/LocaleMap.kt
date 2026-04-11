package com.charles.livecaptionn.settings

/**
 * Best-effort mapping from a bare ISO-639-1 code (used throughout the app
 * to identify a language) to a full BCP-47 locale tag that the Android
 * [android.speech.SpeechRecognizer] expects. For codes we don't recognise,
 * we fall back to `<code>` which most recognisers still accept.
 */
object LocaleMap {
    private val LOCALES = mapOf(
        "en" to "en-US",
        "vi" to "vi-VN",
        "es" to "es-ES",
        "fr" to "fr-FR",
        "de" to "de-DE",
        "it" to "it-IT",
        "pt" to "pt-PT",
        "nl" to "nl-NL",
        "pl" to "pl-PL",
        "ru" to "ru-RU",
        "uk" to "uk-UA",
        "cs" to "cs-CZ",
        "tr" to "tr-TR",
        "ar" to "ar-SA",
        "fa" to "fa-IR",
        "hi" to "hi-IN",
        "bn" to "bn-IN",
        "zh" to "zh-CN",
        "ja" to "ja-JP",
        "ko" to "ko-KR",
        "id" to "id-ID",
        "th" to "th-TH",
        "sv" to "sv-SE",
        "da" to "da-DK",
        "fi" to "fi-FI",
        "no" to "nb-NO",
        "ro" to "ro-RO",
        "hu" to "hu-HU",
        "el" to "el-GR",
        "he" to "he-IL"
    )

    fun bcp47(code: String): String {
        if (code.isBlank()) return "en-US"
        if (code.contains('-')) return code
        return LOCALES[code.lowercase()] ?: code.lowercase()
    }
}
