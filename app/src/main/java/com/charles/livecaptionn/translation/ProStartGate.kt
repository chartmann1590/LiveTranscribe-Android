package com.charles.livecaptionn.translation

import com.charles.livecaptionn.settings.TranslationBackend

/**
 * Whether a caption session with these languages needs Pro.
 *
 * Shared by the Start button (which opens the upgrade flow) and the caption
 * service (which refuses to start), so the two can never disagree. A Pro-only
 * language can stay selected after Pro lapses, which is why Start has to check.
 */
object ProStartGate {
    fun requiresPro(
        backend: TranslationBackend,
        sourceLanguageCode: String,
        targetLanguageCode: String,
        hasPro: Boolean
    ): Boolean = !hasPro &&
        backend == TranslationBackend.ML_KIT &&
        (MlKitLanguages.requiresPro(sourceLanguageCode) || MlKitLanguages.requiresPro(targetLanguageCode))
}
