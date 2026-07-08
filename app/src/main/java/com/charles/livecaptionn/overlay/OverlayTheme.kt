package com.charles.livecaptionn.overlay

import android.graphics.Color
import android.graphics.Typeface

/** [backgroundRgb]/[textRgb] are opaque RGB ints; alpha is applied separately from overlay opacity. */
data class OverlayTheme(
    val id: String,
    val label: String,
    val backgroundRgb: Int,
    val textRgb: Int
)

object OverlayThemeCatalog {
    const val FREE_THEME_ID = "default"

    val THEMES: List<OverlayTheme> = listOf(
        OverlayTheme("default", "Default", backgroundRgb = Color.rgb(17, 17, 17), textRgb = Color.WHITE),
        OverlayTheme("midnight", "Midnight Blue", backgroundRgb = Color.rgb(10, 18, 40), textRgb = Color.rgb(210, 225, 255)),
        OverlayTheme("sunset", "Sunset", backgroundRgb = Color.rgb(48, 20, 20), textRgb = Color.rgb(255, 214, 165)),
        OverlayTheme("highContrast", "High Contrast", backgroundRgb = Color.BLACK, textRgb = Color.YELLOW)
    )

    fun find(id: String): OverlayTheme = THEMES.firstOrNull { it.id == id } ?: THEMES.first()
}

data class OverlayFont(val id: String, val label: String, val typeface: Typeface)

object OverlayFontCatalog {
    const val FREE_FONT_ID = "default"

    val FONTS: List<OverlayFont> = listOf(
        OverlayFont("default", "Default", Typeface.DEFAULT),
        OverlayFont("rounded", "Rounded", Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)),
        OverlayFont("monospace", "Monospace", Typeface.MONOSPACE),
        OverlayFont("serif", "Serif", Typeface.SERIF)
    )

    fun find(id: String): OverlayFont = FONTS.firstOrNull { it.id == id } ?: FONTS.first()
}
