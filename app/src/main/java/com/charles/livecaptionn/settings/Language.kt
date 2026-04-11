package com.charles.livecaptionn.settings

/**
 * A selectable source/target language identified by a two-letter (or BCP-47)
 * code plus a human-readable display name. Used for both LibreTranslate-
 * reported languages and locally installed Vosk models.
 */
data class Language(
    val code: String,
    val name: String
) {
    companion object {
        const val AUTO_CODE = "auto"
        val AUTO = Language(AUTO_CODE, "Auto-detect")

        /** Fallback list used when LibreTranslate is unreachable at startup. */
        val FALLBACK: List<Language> = listOf(
            Language("en", "English"),
            Language("vi", "Vietnamese"),
            Language("es", "Spanish"),
            Language("fr", "French"),
            Language("de", "German"),
            Language("it", "Italian"),
            Language("pt", "Portuguese"),
            Language("nl", "Dutch"),
            Language("pl", "Polish"),
            Language("ru", "Russian"),
            Language("uk", "Ukrainian"),
            Language("cs", "Czech"),
            Language("tr", "Turkish"),
            Language("ar", "Arabic"),
            Language("fa", "Persian"),
            Language("hi", "Hindi"),
            Language("bn", "Bengali"),
            Language("zh", "Chinese"),
            Language("ja", "Japanese"),
            Language("ko", "Korean"),
            Language("id", "Indonesian"),
            Language("th", "Thai"),
        )

        fun displayNameFor(code: String, catalog: List<Language>): String {
            if (code.isBlank()) return code
            if (code == AUTO_CODE) return AUTO.name
            val match = catalog.firstOrNull { it.code.equals(code, ignoreCase = true) }
            if (match != null) return match.name
            val fb = FALLBACK.firstOrNull { it.code.equals(code, ignoreCase = true) }
            return fb?.name ?: code.uppercase()
        }
    }
}
