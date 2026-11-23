package com.charles.livecaptionn.speech

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Wrapper around the Vosk Kotlin API with two modes:
 *
 * 1. Legacy batch [transcribe] — creates a one-shot Recognizer per call. Kept
 *    only as a fallback for the old system-audio batch path.
 *
 * 2. [openSession] — returns a [VoskStreamingSession] that keeps ONE Recognizer
 *    alive across an entire caption session. Callers feed PCM as it arrives
 *    and pull partial/final results. This is how Vosk is actually designed to
 *    be used and is what makes live captions feel live.
 *
 * Models are cached by language code so back-to-back sessions in the same
 * language don't pay the disk-load cost. Model access is guarded by a mutex
 * to prevent races between concurrent openSession/transcribe calls.
 */
class LocalVoskSttClient(private val registry: VoskModelRegistry) {
    private var cachedLanguageCode: String? = null
    private var cachedModel: Model? = null
    private val modelMutex = Mutex()

    data class OpenSessionOutcome(
        val session: VoskStreamingSession?,
        val errorMessage: String? = null
    )

    /** Open a streaming session for [languageCode] at [sampleRate]. Returns null if unavailable. */
    suspend fun openSession(
        languageCode: String,
        sampleRate: Int
    ): VoskStreamingSession? = openSessionWithOutcome(languageCode, sampleRate).session

    /** Open a streaming session and return a user-facing error message if model load fails. */
    suspend fun openSessionWithOutcome(
        languageCode: String,
        sampleRate: Int
    ): OpenSessionOutcome = withContext(Dispatchers.IO) {
        val model = try {
            modelFor(languageCode)
        } catch (e: java.io.IOException) {
            Log.w("LocalVoskSttClient", "Failed to load Vosk model for $languageCode; invalidating", e)
            clearCachedModel(languageCode)
            registry.invalidateInstalledModel(languageCode)
            return@withContext OpenSessionOutcome(
                null,
                "The '$languageCode' speech model could not be loaded (files may be corrupted). Please re-download it."
            )
        } catch (t: Throwable) {
            Log.w("LocalVoskSttClient", "openSession modelFor failed for $languageCode", t)
            return@withContext OpenSessionOutcome(null, localFailureMessage(languageCode, t))
        }
        if (model == null) {
            return@withContext OpenSessionOutcome(
                null,
                "No on-device model installed for '$languageCode'. Open the language picker to download one."
            )
        }
        try {
            val recognizer = Recognizer(model, sampleRate.toFloat()).apply {
                setWords(false)
                setMaxAlternatives(0)
            }
            OpenSessionOutcome(VoskStreamingSession(recognizer))
        } catch (e: java.io.IOException) {
            Log.w("LocalVoskSttClient", "Recognizer creation failed for $languageCode; invalidating", e)
            clearCachedModel(languageCode)
            registry.invalidateInstalledModel(languageCode)
            OpenSessionOutcome(
                null,
                "The '$languageCode' speech model could not be loaded (files may be corrupted). Please re-download it."
            )
        } catch (t: Throwable) {
            Log.w("LocalVoskSttClient", "openSession failed for $languageCode", t)
            OpenSessionOutcome(null, localFailureMessage(languageCode, t))
        }
    }

    /** Legacy batch API, kept for fallbacks. Prefer [openSession]. */
    suspend fun transcribe(
        pcmData: ByteArray,
        sampleRate: Int,
        languageCode: String
    ): TranscribeOutcome = withContext(Dispatchers.IO) {
        try {
            val model = modelFor(languageCode)
                ?: return@withContext TranscribeOutcome(
                    "",
                    "No local Vosk model installed for '$languageCode'. Download it from the language picker."
                )
            Recognizer(model, sampleRate.toFloat()).use { recognizer ->
                val accepted = recognizer.acceptWaveForm(pcmData, pcmData.size)
                val text = when {
                    accepted -> extractText(recognizer.result)
                    else -> extractText(recognizer.partialResult)
                }.ifBlank {
                    extractText(recognizer.finalResult)
                }
                TranscribeOutcome(text)
            }
        } catch (e: java.io.IOException) {
            Log.w("LocalVoskSttClient", "Local transcription failed due to unreadable model", e)
            clearCachedModel(languageCode)
            registry.invalidateInstalledModel(languageCode)
            TranscribeOutcome(
                "",
                "The '$languageCode' speech model could not be loaded (files may be corrupted). Please re-download it."
            )
        } catch (t: Throwable) {
            Log.w("LocalVoskSttClient", "Local transcription failed", t)
            TranscribeOutcome("", localFailureMessage(languageCode, t))
        }
    }

    private suspend fun clearCachedModel(languageCode: String) = modelMutex.withLock {
        if (cachedLanguageCode.equals(languageCode, ignoreCase = true)) {
            try { cachedModel?.close() } catch (_: Throwable) {}
            cachedModel = null
            cachedLanguageCode = null
        }
    }

    private suspend fun modelFor(languageCode: String): Model? = modelMutex.withLock {
        val cached = cachedModel
        if (cached != null && cachedLanguageCode.equals(languageCode, ignoreCase = true)) return cached

        cachedModel?.close()
        cachedModel = null
        cachedLanguageCode = null

        val modelDir = registry.resolveModelDir(languageCode) ?: return@withLock null
        return Model(modelDir.absolutePath).also {
            cachedLanguageCode = languageCode
            cachedModel = it
        }
    }

    private fun localFailureMessage(languageCode: String, t: Throwable): String {
        val detail = t.message?.takeIf { it.isNotBlank() } ?: t::class.java.simpleName
        return "Local '$languageCode' transcription failed: $detail"
    }

    companion object {
        internal fun extractText(resultJson: String): String {
            return try {
                val json = JSONObject(resultJson)
                json.optString("text")
                    .ifBlank { json.optString("partial") }
                    .trim()
            } catch (_: Throwable) {
                ""
            }
        }
    }
}

/**
 * Live streaming Vosk session. Feed PCM chunks as they arrive; pull
 * partial/final text after each feed. Not thread-safe — call from a single
 * worker coroutine.
 */
class VoskStreamingSession internal constructor(
    private val recognizer: Recognizer
) {
    private val lock = Any()
    @Volatile private var closed = false

    /**
     * Feed a chunk of 16-bit LE PCM. Returns [FeedResult] indicating whether a
     * final result is available (`accepted == true` means Vosk detected a
     * silence/segment boundary) plus the current text — if `accepted`, [text]
     * is the finalized segment; otherwise it's the latest partial.
     */
    fun feed(pcm: ByteArray, length: Int = pcm.size): FeedResult = synchronized(lock) {
        if (closed) return FeedResult("", accepted = false)
        val accepted = try {
            recognizer.acceptWaveForm(pcm, length)
        } catch (t: Throwable) {
            Log.w("VoskStreamingSession", "acceptWaveForm failed", t)
            return FeedResult("", accepted = false)
        }
        val text = if (accepted) {
            LocalVoskSttClient.extractText(recognizer.result)
        } else {
            LocalVoskSttClient.extractText(recognizer.partialResult)
        }
        FeedResult(text, accepted)
    }

    /** Flushes any buffered audio and returns the final remaining text. */
    fun finish(): String = synchronized(lock) {
        if (closed) return ""
        return try {
            LocalVoskSttClient.extractText(recognizer.finalResult)
        } catch (t: Throwable) {
            Log.w("VoskStreamingSession", "finalResult failed", t)
            ""
        }
    }

    fun close() = synchronized(lock) {
        if (closed) return
        closed = true
        try { recognizer.close() } catch (_: Throwable) {}
    }

    data class FeedResult(
        /** Current text — segment if [accepted], partial otherwise. */
        val text: String,
        /** True when Vosk hit a silence boundary and emitted a finalized segment. */
        val accepted: Boolean
    )
}
