package com.charles.livecaptionn.service

/**
 * Pure functions extracted from [CaptionForegroundService] for testability.
 *
 * These transforms drive the core captioning pipeline: recording incoming
 * transcript lines, pairing them with translations, and rendering the
 * scrolling overlay body.
 */

/** Maximum number of transcript lines kept in the scroll-back buffer. */
internal const val MAX_TRANSCRIPT_LINES = 30

/**
 * Record a new STT result into the transcript line list.
 *
 * @param lines        Current transcript lines (scroll-back buffer).
 * @param originalText The latest recognized text from STT.
 * @param replaceOpenLine If true, replace the last open (untranslated) line
 *                        instead of appending (used for partial results).
 * @return The updated line list, trimmed to [MAX_TRANSCRIPT_LINES].
 */
internal fun recordTranscriptResult(
    lines: List<CaptionRuntimeLine>,
    originalText: String,
    replaceOpenLine: Boolean
): List<CaptionRuntimeLine> {
    if (originalText.isBlank()) return lines
    val last = lines.lastOrNull()
    if (last?.originalText == originalText && last.translatedText.isBlank()) return lines
    if (replaceOpenLine && last?.translatedText.isNullOrBlank()) {
        return (lines.dropLast(1) + CaptionRuntimeLine(originalText = originalText))
            .takeLast(MAX_TRANSCRIPT_LINES)
    }
    return (lines + CaptionRuntimeLine(originalText = originalText)).takeLast(MAX_TRANSCRIPT_LINES)
}

/**
 * Pair a translated text with its matching transcript line by matching the
 * original text. If no exact match is found, the result is considered stale
 * and discarded (prevents orphan lines that cause double captions).
 *
 * @param lines          Current transcript lines.
 * @param originalText   The original text that was sent for translation.
 * @param translatedText The translation result to attach.
 * @return Updated lines with the translation filled in (or unchanged if no match).
 */
internal fun updateTranscriptTranslation(
    lines: List<CaptionRuntimeLine>,
    originalText: String,
    translatedText: String
): List<CaptionRuntimeLine> {
    if (originalText.isBlank()) return lines
    val existingIndex = lines.indexOfLast { it.originalText == originalText }
    if (existingIndex >= 0) {
        return lines.mapIndexed { index, line ->
            if (index == existingIndex) line.copy(translatedText = translatedText) else line
        }
    }
    return lines
}

/**
 * Build the scrolling overlay body text. Shows only translated lines;
 * untranslated lines are hidden so the user doesn't see both raw source
 * and translation stacked together.
 */
internal fun overlayTranscriptText(runtime: CaptionRuntimeState): String {
    val rendered = runtime.transcriptLines
        .mapNotNull { it.translatedText.trim().takeIf { t -> t.isNotBlank() } }
    val historyText = if (rendered.isNotEmpty()) rendered.joinToString("\n") else ""
    val livePartial = runtime.translatedText.trim()
    return when {
        historyText.isBlank() -> livePartial
        livePartial.isBlank() -> historyText
        historyText.endsWith(livePartial) -> historyText
        else -> "$historyText\n$livePartial"
    }
}
