package com.charles.livecaptionn.ui.premium

/**
 * Which Pro-locked thing the user just tapped. The upgrade dialog opens at that
 * moment of value and names it, instead of a generic "Go Pro".
 */
enum class ProTrigger {
    GENERIC,
    SERVER_MODEL,
    LANGUAGE,
    GLOSSARY,
    PROFILE,
    THEME
}
