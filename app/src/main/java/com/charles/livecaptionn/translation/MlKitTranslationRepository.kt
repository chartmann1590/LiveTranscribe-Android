package com.charles.livecaptionn.translation

import android.util.Log
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
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
 * `de`, `ja`, `ko`, `zh`, etc.).
 *
 * Return-value contract:
 *   - Identity (source == target) or blank input → original text.
 *   - Unsupported pair (including an `"auto"` source — ML Kit Translate has
 *     no language identification) → empty string, with the reason surfaced
 *     as an error on [statusFlow]. Never the original text: silently
 *     passing it through looks exactly like a translation that rendered
 *     the source unchanged, which is the bug this guard exists to prevent.
 *   - Runtime failure (download stall, model load error, etc.) → empty
 *     string. The service treats an empty result as "don't overwrite the
 *     last good translation", and [statusFlow] surfaces the reason to the UI.
 */
class MlKitTranslationRepository : TranslationRepository {

    private class TranslationTimeoutException(message: String) : IllegalStateException(message)

    // One Translator instance per source|target pair, cached for the
    // lifetime of the process so downloads and model loads only happen once.
    private val translators = mutableMapOf<String, Translator>()
    private val readyPairs = ConcurrentHashMap.newKeySet<String>()
    private val pairDownloadLocks = ConcurrentHashMap<String, Mutex>()
    private val recentFailures = ConcurrentHashMap<String, Pair<Long, String>>()

    private val mutableStatus = MutableStateFlow(TranslationStatus())
    override val statusFlow: StateFlow<TranslationStatus> = mutableStatus.asStateFlow()

    override suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String {
        val clean = text.trim()
        if (clean.isEmpty()) return clean
        if (targetCode.isBlank()) return clean

        // Fail loudly (empty result + error status) for pairs ML Kit cannot
        // translate — most importantly an "auto" source, which previously
        // fell through to a silent passthrough of the original text.
        MlKitLanguages.unsupportedPairMessage(sourceCode, targetCode)?.let { message ->
            mutableStatus.value = TranslationStatus(
                downloading = false,
                message = message,
                isError = true
            )
            return ""
        }

        val src = TranslateLanguage.fromLanguageTag(sourceCode.trim().lowercase())
        val dst = TranslateLanguage.fromLanguageTag(targetCode.trim().lowercase())
        if (src == null || dst == null) {
            val message = MlKitLanguages.unsupportedPairMessage(sourceCode, targetCode)
                ?: "Translation language not supported: $sourceCode → $targetCode. " +
                    "Choose a supported language."
            Log.w(TAG, "ML Kit does not support pair $sourceCode → $targetCode.")
            mutableStatus.value = TranslationStatus(
                downloading = false,
                message = message,
                isError = true
            )
            return ""
        }
        if (src == dst) {
            mutableStatus.value = TranslationStatus()
            return clean
        }

        val key = "$src|$dst"
        recentFailures[key]?.let { (failedAtNanos, msg) ->
            if (System.nanoTime() - failedAtNanos < FAILED_RETRY_COOLDOWN_NANOS) {
                mutableStatus.value = TranslationStatus(downloading = false, message = msg, isError = true)
                return ""
            }
            recentFailures.remove(key)
        }
        return try {
            val translator = ensurePairReady(src, dst)
            val translated = withTimeoutOrNull(TRANSLATE_TIMEOUT_MS) {
                translateBlocking(translator, clean)
            }?.trim() ?: throw TranslationTimeoutException("Translation timed out.")
            recentFailures.remove(key)
            mutableStatus.value = TranslationStatus()
            Log.d(TAG, "ML Kit $sourceCode→$targetCode ok: '${clean.take(40)}' → '${translated.take(40)}'")
            translated
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            evictTranslator(key)
            val msg = describeFailure(t)
            recentFailures[key] = System.nanoTime() to msg
            mutableStatus.value = TranslationStatus(downloading = false, message = msg, isError = true)
            Log.w(TAG, "ML Kit translation failed for $sourceCode→$targetCode: $msg", t)
            ""
        }
    }

    override suspend fun prewarm(sourceCode: String, targetCode: String) {
        MlKitLanguages.unsupportedPairMessage(sourceCode, targetCode)?.let { message ->
            mutableStatus.value = TranslationStatus(
                downloading = false,
                message = message,
                isError = true
            )
            return
        }
        val src = TranslateLanguage.fromLanguageTag(sourceCode.trim().lowercase()) ?: return
        val dst = TranslateLanguage.fromLanguageTag(targetCode.trim().lowercase()) ?: return
        if (src == dst) {
            mutableStatus.value = TranslationStatus()
            return
        }
        val key = "$src|$dst"
        recentFailures.remove(key)
        try {
            ensurePairReady(src, dst)
            mutableStatus.value = TranslationStatus()
            Log.i(TAG, "ML Kit model ready for $sourceCode → $targetCode")
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            evictTranslator(key)
            val msg = describeFailure(t)
            recentFailures[key] = System.nanoTime() to msg
            mutableStatus.value = TranslationStatus(downloading = false, message = msg, isError = true)
            Log.w(TAG, "ML Kit prewarm failed for $sourceCode → $targetCode: $msg", t)
        }
    }

    /**
     * Ensures the source→target model is downloaded, propagating failures
     * (unlike [prewarm], which swallows them for the caption service). Used by
     * UI localization so a stalled model download can bail to the server
     * fallback instead of spinning forever.
     */
    suspend fun requireModel(sourceCode: String, targetCode: String) {
        val src = TranslateLanguage.fromLanguageTag(sourceCode.trim().lowercase()) ?: return
        val dst = TranslateLanguage.fromLanguageTag(targetCode.trim().lowercase()) ?: return
        if (src == dst) return
        val key = "$src|$dst"
        recentFailures.remove(key)
        try {
            ensurePairReady(src, dst)
            mutableStatus.value = TranslationStatus()
            Log.i(TAG, "ML Kit model ready for $sourceCode → $targetCode")
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            evictTranslator(key)
            val msg = describeFailure(t)
            recentFailures[key] = System.nanoTime() to msg
            mutableStatus.value = TranslationStatus(downloading = false, message = msg, isError = true)
            throw t
        }
    }

    private suspend fun ensurePairReady(src: String, dst: String): Translator {
        val key = "$src|$dst"
        val translator = translatorFor(src, dst)
        if (readyPairs.contains(key)) return translator

        val lock = pairDownloadLocks.computeIfAbsent(key) { Mutex() }
        return lock.withLock {
            if (readyPairs.contains(key)) return@withLock translator
            val missing = missingLanguageModels(src, dst)
            if (missing.isNotEmpty()) {
                val labels = missing.map { languageLabel(it) }.distinct()
                val downloadMsg = if (labels.size == 1) {
                    "Downloading ${labels[0]} translation model…"
                } else {
                    "Downloading ${labels.joinToString(" & ")} translation models…"
                }
                mutableStatus.value = TranslationStatus(
                    downloading = true,
                    message = downloadMsg,
                    isError = false
                )
            }
            try {
                withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
                    ensureModelDownloaded(translator)
                } ?: throw TranslationTimeoutException(
                    "Translation model download timed out. Check your internet connection."
                )
                readyPairs.add(key)
                recentFailures.remove(key)
                mutableStatus.value = TranslationStatus()
                translator
            } catch (ce: CancellationException) {
                mutableStatus.value = TranslationStatus()
                throw ce
            }
        }
    }

    private suspend fun missingLanguageModels(src: String, dst: String): List<String> {
        val manager = RemoteModelManager.getInstance()
        val required = buildList {
            add(src)
            add(dst)
            if (src != TranslateLanguage.ENGLISH && dst != TranslateLanguage.ENGLISH) {
                add(TranslateLanguage.ENGLISH)
            }
        }.distinct()
        return required.filterNot { lang -> isLanguageModelDownloaded(manager, lang) }
    }

    private suspend fun isLanguageModelDownloaded(manager: RemoteModelManager, lang: String): Boolean =
        try {
            withContext(Dispatchers.IO) {
                suspendCancellableCoroutine { cont ->
                    val model = TranslateRemoteModel.Builder(lang).build()
                    manager.isModelDownloaded(model)
                        .addOnSuccessListener { downloaded -> if (cont.isActive) cont.resume(downloaded) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(false) }
                        .addOnCanceledListener { if (cont.isActive) cont.resume(false) }
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            false
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

    @Synchronized
    private fun evictTranslator(key: String) {
        readyPairs.remove(key)
        translators.remove(key)?.let { translator ->
            try { translator.close() } catch (_: Throwable) {}
        }
    }

    private suspend fun ensureModelDownloaded(translator: Translator) = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine<Unit> { cont ->
            // DownloadConditions defaults allow cellular — we don't force
            // Wi-Fi so the first-use download actually completes when the
            // user isn't on Wi-Fi.
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
                .addOnCanceledListener {
                    if (cont.isActive) {
                        cont.resumeWithException(IllegalStateException("ML Kit model download was canceled"))
                    }
                }
        }
    }

    private suspend fun translateBlocking(translator: Translator, text: String): String =
        suspendCancellableCoroutine { cont ->
            translator.translate(text)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
                .addOnCanceledListener {
                    if (cont.isActive) {
                        cont.resumeWithException(IllegalStateException("ML Kit translation was canceled"))
                    }
                }
        }

    private fun languageLabel(rawCode: String): String {
        val code = rawCode.trim()
        return MlKitLanguages.LIST.firstOrNull { it.code.equals(code, ignoreCase = true) }?.name
            ?: code.uppercase()
    }

    private fun describeFailure(t: Throwable): String = when (t) {
        is TranslationTimeoutException -> t.message ?: "Translation timed out."
        is MlKitException -> when (t.errorCode) {
            MlKitException.NETWORK_ISSUE ->
                "Translation model download failed: check your internet connection."
            MlKitException.NOT_ENOUGH_SPACE ->
                "Translation model download failed: not enough storage space."
            MlKitException.UNAVAILABLE ->
                "On-device translation service is temporarily unavailable."
            else -> "Translation failed: ${t.localizedMessage ?: "ML Kit error (${t.errorCode})"}"
        }
        else -> "Translation failed: ${t.message?.takeIf { it.isNotBlank() } ?: t::class.java.simpleName}"
    }

    override fun close() {
        val toClose = synchronized(this) {
            readyPairs.clear()
            recentFailures.clear()
            val current = translators.values.toList()
            translators.clear()
            current
        }
        toClose.forEach { translator ->
            try { translator.close() } catch (_: Throwable) {}
        }
    }

    companion object {
        private const val TAG = "MlKitTranslator"
        private const val DOWNLOAD_TIMEOUT_MS = 90_000L
        private const val TRANSLATE_TIMEOUT_MS = 15_000L
        private const val FAILED_RETRY_COOLDOWN_NANOS = 10_000_000_000L
    }
}
