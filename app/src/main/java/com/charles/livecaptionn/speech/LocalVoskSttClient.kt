package com.charles.livecaptionn.speech

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Thin wrapper around the Vosk Kotlin API. Resolves the on-disk model
 * directory via [VoskModelRegistry] and caches the loaded [Model] so
 * consecutive transcriptions in the same language don't pay the
 * load-from-disk cost every chunk.
 */
class LocalVoskSttClient(private val registry: VoskModelRegistry) {
    private var cachedLanguageCode: String? = null
    private var cachedModel: Model? = null

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
                Log.d("LocalVoskSttClient", "Local STT accepted=$accepted text=$text")
                TranscribeOutcome(text)
            }
        } catch (t: Throwable) {
            Log.w("LocalVoskSttClient", "Local transcription failed", t)
            TranscribeOutcome("", localFailureMessage(languageCode, t))
        }
    }

    private fun modelFor(languageCode: String): Model? {
        val cached = cachedModel
        if (cached != null && cachedLanguageCode.equals(languageCode, ignoreCase = true)) return cached

        cachedModel?.close()
        cachedModel = null
        cachedLanguageCode = null

        val modelDir = registry.resolveModelDir(languageCode) ?: return null
        return Model(modelDir.absolutePath).also {
            cachedLanguageCode = languageCode
            cachedModel = it
        }
    }

    private fun localFailureMessage(languageCode: String, t: Throwable): String {
        val detail = t.message?.takeIf { it.isNotBlank() } ?: t::class.java.simpleName
        return "Local '$languageCode' transcription failed: $detail"
    }

    private fun extractText(resultJson: String): String {
        val json = JSONObject(resultJson)
        return json.optString("text")
            .ifBlank { json.optString("partial") }
            .trim()
    }
}
