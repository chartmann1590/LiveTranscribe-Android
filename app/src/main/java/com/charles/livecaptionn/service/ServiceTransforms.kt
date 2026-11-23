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
 * Output of recording an STT result, pairing the updated scroll-back list with
 * the stable [lineId] of the line that holds [originalText] (which may be a
 * reused open line's id rather than the candidate id).
 */
internal data class RecordedTranscript(
    val lines: List<CaptionRuntimeLine>,
    val lineId: Long
)

/**
 * Record a new STT result into the transcript line list.
 *
 * Open/closed state is driven solely by [isFinal] (Vosk's own segment
 * boundary), never by whether a translation has landed yet — a translation
 * can land mid-sentence (e.g. during a natural speech pause that triggers
 * the translate debounce but not Vosk's finalizer), and must not cause the
 * next partial of the same utterance to be appended as a duplicate line.
 *
 * @param lines        Current transcript lines (scroll-back buffer).
 * @param originalText The latest recognized text from STT.
 * @param isFinal      Whether this result is Vosk's finalized segment.
 * @param newLineId    Id to assign if this text starts/becomes a new version.
 * @return The updated line list, trimmed to [MAX_TRANSCRIPT_LINES].
 */
internal fun recordTranscriptResult(
    lines: List<CaptionRuntimeLine>,
    originalText: String,
    isFinal: Boolean,
    newLineId: Long
): List<CaptionRuntimeLine> =
    recordTranscriptResultWithId(lines, originalText, isFinal, newLineId).lines

/**
 * Same as [recordTranscriptResult], additionally returning the actual id of
 * the line containing [originalText] so translation requests always reconcile
 * against the committed line id (even when transitioning an existing open
 * partial with identical text to `isFinal = true`).
 */
internal fun recordTranscriptResultWithId(
    lines: List<CaptionRuntimeLine>,
    originalText: String,
    isFinal: Boolean,
    newLineId: Long,
    noLineId: Long = -1L
): RecordedTranscript {
    if (originalText.isBlank()) return RecordedTranscript(lines, noLineId)
    val last = lines.lastOrNull()
    val lastIsOpen = last != null && !last.isFinal

    if (lastIsOpen) {
        if (last!!.originalText == originalText && last.isFinal == isFinal) {
            return RecordedTranscript(lines, last.id)
        }
        val updatedLine = if (last.originalText == originalText) {
            last.copy(isFinal = isFinal)
        } else {
            CaptionRuntimeLine(id = newLineId, originalText = originalText, isFinal = isFinal)
        }
        return RecordedTranscript(
            lines = (lines.dropLast(1) + updatedLine).takeLast(MAX_TRANSCRIPT_LINES),
            lineId = updatedLine.id
        )
    }

    val appended = CaptionRuntimeLine(id = newLineId, originalText = originalText, isFinal = isFinal)
    return RecordedTranscript(
        lines = (lines + appended).takeLast(MAX_TRANSCRIPT_LINES),
        lineId = appended.id
    )
}

/**
 * Pair a translated text with its matching transcript line by id. If the id
 * is no longer present (the line's text has since evolved past this
 * translation's snapshot), the result is considered stale and discarded
 * (prevents orphan/stale translations from causing double captions).
 *
 * @param lines          Current transcript lines.
 * @param lineId         Id of the line this translation was requested for.
 * @param translatedText The translation result to attach.
 * @return Updated lines with the translation filled in (or unchanged if no match).
 */
internal fun updateTranscriptTranslation(
    lines: List<CaptionRuntimeLine>,
    lineId: Long,
    translatedText: String
): List<CaptionRuntimeLine> {
    val existingIndex = lines.indexOfFirst { it.id == lineId }
    if (existingIndex < 0) return lines
    return lines.mapIndexed { index, line ->
        if (index == existingIndex) line.copy(translatedText = translatedText) else line
    }
}

/**
 * Result of applying a completed translation snapshot to the current transcript
 * state, including updated scroll-back lines and live preview state.
 */
internal data class AppliedTranslation(
    val lines: List<CaptionRuntimeLine>,
    val translatedText: String,
    val lastAppliedLiveLineId: Long
)

/**
 * Applies a completed translation for [requestLineId] to [lines] and the live
 * preview [currentTranslatedText]. If an open utterance evolved to a newer id
 * while an in-flight partial was translating, updates the live preview while
 * leaving [lines] untouched so stale partials never attach to newer line ids.
 */
internal fun applyTranslationResult(
    lines: List<CaptionRuntimeLine>,
    currentTranslatedText: String,
    lastAppliedLiveLineId: Long,
    requestLineId: Long,
    isFinal: Boolean,
    translatedText: String,
    noLineId: Long = -1L
): AppliedTranslation {
    val clean = translatedText.trim()
    if (clean.isEmpty()) {
        return AppliedTranslation(lines, currentTranslatedText, lastAppliedLiveLineId)
    }

    val updatedLines = updateTranscriptTranslation(lines, requestLineId, clean)
    val matchedExistingLine = lines.any { it.id == requestLineId }

    if (matchedExistingLine) {
        val isLatestLine = updatedLines.lastOrNull()?.id == requestLineId
        val isNewerOrEqualLive = lastAppliedLiveLineId == noLineId || requestLineId >= lastAppliedLiveLineId
        val shouldUpdateLive = isLatestLine || isNewerOrEqualLive || currentTranslatedText.isBlank()
        return AppliedTranslation(
            lines = updatedLines,
            translatedText = if (shouldUpdateLive) clean else currentTranslatedText,
            lastAppliedLiveLineId = if (shouldUpdateLive) requestLineId else lastAppliedLiveLineId
        )
    }

    // Open line evolved to a newer id while a partial translation was in flight.
    // Keep the line list untouched (so stale partials never attach to newer ids),
    // but update the live preview if this partial belongs to the active open utterance.
    if (!isFinal) {
        val lastLine = lines.lastOrNull()
        val lastFinalId = lines.lastOrNull { it.isFinal }?.id ?: noLineId
        val belongsToActiveOpenUtterance = lastLine != null &&
            !lastLine.isFinal &&
            requestLineId > lastFinalId &&
            requestLineId <= lastLine.id
        val isNewerOrEqualLive = lastAppliedLiveLineId == noLineId || requestLineId >= lastAppliedLiveLineId
        if (belongsToActiveOpenUtterance && isNewerOrEqualLive) {
            return AppliedTranslation(
                lines = lines,
                translatedText = clean,
                lastAppliedLiveLineId = requestLineId
            )
        }
    }

    return AppliedTranslation(lines, currentTranslatedText, lastAppliedLiveLineId)
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
