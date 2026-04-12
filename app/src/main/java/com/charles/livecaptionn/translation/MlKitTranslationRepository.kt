package com.charles.livecaptionn.translation

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device translation powered by Google ML Kit.
 *
 * ML Kit ships pre-trained Google Translate models that run entirely on the
 * phone once downloaded. First use of a given language pair triggers a
 * one-time ~30 MB download (handled transparently). After that the pipeline
 * is fully offline — no LibreTranslate server required.
 *
 * Supported languages are whatever `TranslateLanguage.getAllLanguages()`
 * exposes — at the time of writing roughly 59 codes (`en`, `vi`, `es`, `fr`,
 * `de`, `ja`, `ko`, `zh`, etc.). Unknown codes fall through to returning the
 * original text.
 */
class MlKitTranslationRepository : TranslationRepository {

    // One Translator instance per source|target pair, cached for the
    // lifetime of the process so downloads and model loads only happen once.
    private val translators = mutableMapOf<String, Translator>()

    override suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String {
        val clean = text.trim()
        if (clean.isEmpty()) return clean
        if (targetCode.isBlank()) return clean

        val src = TranslateLanguage.fromLanguageTag(sourceCode.lowercase())
        val dst = TranslateLanguage.fromLanguageTag(targetCode.lowercase())
        if (src == null || dst == null) {
            Log.w(TAG, "ML Kit does not support pair $sourceCode → $targetCode; passing text through.")
            return clean
        }
        if (src == dst) return clean

        return try {
            val translator = translatorFor(src, dst)
            ensureModelDownloaded(translator)
            translateBlocking(translator, clean)
        } catch (t: Throwable) {
            Log.w(TAG, "ML Kit translation failed; returning original text.", t)
            clean
        }
    }

    @Synchronized
    private fun translatorFor(src: String, dst: String): Translator {
        val key = "$src|$dst"
        translators[key]?.let { return it }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(src)
            .setTargetLanguage(dst)
            .build()
        val translator = Translation.getClient(options)
        translators[key] = translator
        return translator
    }

    private suspend fun ensureModelDownloaded(translator: Translator) = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine<Unit> { cont ->
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }

    private suspend fun translateBlocking(translator: Translator, text: String): String =
        suspendCancellableCoroutine { cont ->
            translator.translate(text)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    companion object {
        private const val TAG = "MlKitTranslator"
    }
}
