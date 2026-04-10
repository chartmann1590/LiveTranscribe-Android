package com.charles.livecaptionn.settings

enum class AppLanguage(val code: String, val label: String) {
    ENGLISH("en", "English"),
    VIETNAMESE("vi", "Vietnamese");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code == code } ?: ENGLISH
        }
    }
}
