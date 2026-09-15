package com.charles.livecaptionn.overlay

/**
 * Resolved visual display properties for the overlay text views.
 *
 * Ensures original speech and translated captions are distinct, cleanly
 * formatted, and do not overlap.
 */
data class OverlayTextDisplay(
    val showOriginal: Boolean,
    val originalText: String,
    val originalTextSizeSp: Float,
    val translatedText: String,
    val translatedTextSizeSp: Float
)

/**
 * Determines text visibility, contents, and proportional scaling for the overlay.
 */
fun computeOverlayTextDisplay(ui: OverlayUiState): OverlayTextDisplay {
    val isIdentical = ui.originalText.isNotBlank() &&
        ui.originalText.trim().equals(ui.transcriptText.trim(), ignoreCase = true)
    val showOriginal = ui.showOriginal && ui.originalText.isNotBlank() && !isIdentical
    val scaledOriginalSize = (ui.textSizeSp * 0.82f).coerceIn(12f, 22f)
    val displayTranslated = ui.transcriptText.ifBlank { "…" }
    return OverlayTextDisplay(
        showOriginal = showOriginal,
        originalText = ui.originalText,
        originalTextSizeSp = scaledOriginalSize,
        translatedText = displayTranslated,
        translatedTextSizeSp = ui.textSizeSp
    )
}
