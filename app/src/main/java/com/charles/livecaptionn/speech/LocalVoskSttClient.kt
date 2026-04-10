package com.charles.livecaptionn.speech

import android.content.Context
import android.util.Log
import com.charles.livecaptionn.settings.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

class LocalVoskSttClient(private val context: Context) {
    private var cachedLanguage: AppLanguage? = null
    private var cachedModel: Model? = null

    suspend fun transcribe(
        pcmData: ByteArray,
        sampleRate: Int,
        language: AppLanguage
    ): TranscribeOutcome = withContext(Dispatchers.IO) {
        try {
            val model = modelFor(language)
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
            TranscribeOutcome("", localFailureMessage(language, t))
        }
    }

    private fun modelFor(language: AppLanguage): Model {
        val cached = cachedModel
        if (cached != null && cachedLanguage == language) return cached

        cachedModel?.close()
        val modelDir = ensureModelCopied(language)
        return Model(modelDir.absolutePath).also {
            cachedLanguage = language
            cachedModel = it
        }
    }

    private fun ensureModelCopied(language: AppLanguage): File {
        val assetModelName = when (language) {
            AppLanguage.ENGLISH -> "vosk-model-small-en-us-0.15"
            AppLanguage.VIETNAMESE -> "vosk-model-small-vn-0.4"
        }
        val assetPath = "models/$assetModelName"
        if (context.assets.list(assetPath).isNullOrEmpty()) {
            error("Local ${language.label} model is missing. Add $assetPath to app/src/main/assets.")
        }

        val targetDir = File(context.filesDir, "vosk/$assetModelName")
        val marker = File(targetDir, ".copied")
        if (!marker.exists()) {
            if (targetDir.exists()) targetDir.deleteRecursively()
            copyAssetDirectory(assetPath, targetDir)
            marker.writeText("ok")
        }
        return targetDir
    }

    private fun copyAssetDirectory(assetPath: String, targetDir: File) {
        targetDir.mkdirs()
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            context.assets.open(assetPath).use { input ->
                targetDir.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        for (child in children) {
            val childAssetPath = "$assetPath/$child"
            val childTarget = File(targetDir, child)
            if (context.assets.list(childAssetPath).isNullOrEmpty()) {
                context.assets.open(childAssetPath).use { input ->
                    childTarget.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                copyAssetDirectory(childAssetPath, childTarget)
            }
        }
    }

    private fun localFailureMessage(language: AppLanguage, t: Throwable): String {
        val detail = t.message?.takeIf { it.isNotBlank() } ?: t::class.java.simpleName
        return "Local ${language.label} transcription failed: $detail"
    }

    private fun extractText(resultJson: String): String {
        val json = JSONObject(resultJson)
        return json.optString("text")
            .ifBlank { json.optString("partial") }
            .trim()
    }
}
