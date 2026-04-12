package com.charles.livecaptionn.translation

import com.charles.livecaptionn.settings.Language

/**
 * Languages supported by Google ML Kit's on-device translation models.
 *
 * Matches `TranslateLanguage.getAllLanguages()` as of ML Kit 17.0.3. Kept as
 * a hardcoded list so that `MainUiState` (a pure data class) can expose it
 * without touching ML Kit APIs — any drift from the SDK is caught at runtime
 * by `TranslateLanguage.fromLanguageTag` inside `MlKitTranslationRepository`,
 * which silently falls through for unknown codes.
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
}
