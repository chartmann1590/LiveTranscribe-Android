package com.charles.livecaptionn.translation

import com.charles.livecaptionn.settings.Language

/**
 * Languages supported by Google ML Kit's on-device translation models.
 *
 * Matches `TranslateLanguage.getAllLanguages()` as of ML Kit 17.0.3. Kept as
 * a hardcoded list so that `MainUiState` (a pure data class) can expose it
 * without touching ML Kit APIs — any drift from the SDK is caught at runtime
 * by `TranslateLanguage.fromLanguageTag` inside `MlKitTranslationRepository`,
 * which now surfaces an error (never a silent passthrough) for unknown codes.
 */
object MlKitLanguages {
    val LIST: List<Language> = listOf(
        Language("af", "Afrikaans"),
        Language("ar", "Arabic"),
        Language("be", "Belarusian"),
        Language("bg", "Bulgarian"),
        Language("bn", "Bengali"),
        Language("ca", "Catalan"),
        Language("cs", "Czech"),
        Language("cy", "Welsh"),
        Language("da", "Danish"),
        Language("de", "German"),
        Language("el", "Greek"),
        Language("en", "English"),
        Language("eo", "Esperanto"),
        Language("es", "Spanish"),
        Language("et", "Estonian"),
        Language("fa", "Persian"),
        Language("fi", "Finnish"),
        Language("fr", "French"),
        Language("ga", "Irish"),
        Language("gl", "Galician"),
        Language("gu", "Gujarati"),
        Language("he", "Hebrew"),
        Language("hi", "Hindi"),
        Language("hr", "Croatian"),
        Language("ht", "Haitian Creole"),
        Language("hu", "Hungarian"),
        Language("id", "Indonesian"),
        Language("is", "Icelandic"),
        Language("it", "Italian"),
        Language("ja", "Japanese"),
        Language("ka", "Georgian"),
        Language("kn", "Kannada"),
        Language("ko", "Korean"),
        Language("lt", "Lithuanian"),
        Language("lv", "Latvian"),
        Language("mk", "Macedonian"),
        Language("mr", "Marathi"),
        Language("ms", "Malay"),
        Language("mt", "Maltese"),
        Language("nl", "Dutch"),
        Language("no", "Norwegian"),
        Language("pl", "Polish"),
        Language("pt", "Portuguese"),
        Language("ro", "Romanian"),
        Language("ru", "Russian"),
        Language("sk", "Slovak"),
        Language("sl", "Slovenian"),
        Language("sq", "Albanian"),
        Language("sv", "Swedish"),
        Language("sw", "Swahili"),
        Language("ta", "Tamil"),
        Language("te", "Telugu"),
        Language("th", "Thai"),
        Language("tl", "Tagalog"),
        Language("tr", "Turkish"),
        Language("uk", "Ukrainian"),
        Language("ur", "Urdu"),
        Language("vi", "Vietnamese"),
        Language("zh", "Chinese"),
    )

    /**
     * Free-tier language codes — a curated set of the most commonly requested
     * pairs. Everything else in [LIST] requires Pro. Purely a UI-level gate
     * (matches this app's existing trust model elsewhere); [LIST] itself and
     * the underlying ML Kit repository stay fully ungated.
     */
    val FREE_TIER: Set<String> = setOf(
        "en", "es", "fr", "de", "it", "pt", "nl", "ru", "zh", "ja", "ko", "ar", "hi", "vi", "tr"
    )

    fun requiresPro(code: String): Boolean = code.trim().lowercase() !in FREE_TIER

    private val SUPPORTED_CODES: Set<String> = LIST.map { it.code }.toSet()

    /** Legacy Java locale aliases ML Kit resolves to codes in [LIST]. */
    private val CODE_ALIASES: Map<String, String> = mapOf(
        "iw" to "he",
        "in" to "id"
    )

    /**
     * Whether [code] names a language ML Kit can translate. Region/variant
     * tags are judged by their base language (`ru-RU` → `ru`). Pure Kotlin so
     * the unsupported-language guard is unit-testable without the ML Kit SDK.
     */
    fun isSupported(code: String): Boolean {
        val base = code.trim().lowercase().substringBefore('-').substringBefore('_')
        if (base.isEmpty()) return false
        return (CODE_ALIASES[base] ?: base) in SUPPORTED_CODES
    }

    /**
     * User-facing error message when the [sourceCode] → [targetCode] pair
     * cannot be translated on-device, or null when the pair is supported.
     * `"auto"` gets its own message: ML Kit Translate has no built-in
     * language identification, so an auto source must never silently fall
     * back to showing the untranslated original.
     */
    fun unsupportedPairMessage(sourceCode: String, targetCode: String): String? {
        if (sourceCode.trim().equals("auto", ignoreCase = true) ||
            targetCode.trim().equals("auto", ignoreCase = true)
        ) {
            return "Auto-detect isn't supported for on-device translation. " +
                "Choose the spoken language instead."
        }
        if (!isSupported(sourceCode)) {
            return "Translation language not supported: '${sourceCode.trim()}'. " +
                "Choose a supported source language."
        }
        if (!isSupported(targetCode)) {
            return "Translation language not supported: '${targetCode.trim()}'. " +
                "Choose a supported target language."
        }
        return null
    }
}
