package com.charles.livecaptionn.ui.l10n

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Persists per-language UI translations as small JSON files so a previously
 * chosen interface language renders instantly on next launch without
 * re-downloading the ML Kit model or re-translating every string.
 *
 * Stored under filesDir, so it's private to the app and survives updates but
 * is cleared on uninstall (consistent with the rest of the app's settings).
 */
class UiTranslationCache(context: Context) {

    private val dir = File(context.filesDir, "ui_translations")

    fun read(languageCode: String): Map<String, String>? {
        val file = fileFor(languageCode) ?: return null
        if (!file.exists()) return null
        return try {
            val obj = JSONObject(file.readText())
            val result = HashMap<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = obj.optString(key)
            }
            result
        } catch (_: Throwable) {
            null
        }
    }

    fun write(languageCode: String, translations: Map<String, String>) {
        val file = fileFor(languageCode) ?: return
        try {
            dir.mkdirs()
            val obj = JSONObject()
            translations.forEach { (key, value) -> obj.put(key, value) }
            file.writeText(obj.toString())
        } catch (_: Throwable) {
            // Cache is best-effort; a write failure just means a re-translate.
        }
    }

    fun delete(languageCode: String) {
        fileFor(languageCode)?.let { runCatching { it.delete() } }
    }

    private fun fileFor(languageCode: String): File? =
        if (languageCode.isBlank()) null else File(dir, "ui_${languageCode.lowercase()}.json")
}