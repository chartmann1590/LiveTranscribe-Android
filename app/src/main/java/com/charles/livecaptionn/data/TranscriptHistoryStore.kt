package com.charles.livecaptionn.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Persists transcript history as a JSON array in internal storage.
 * Thread-safe via Mutex; keeps the most recent [MAX_ENTRIES] entries.
 */
class TranscriptHistoryStore private constructor(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, "transcript_history.json"))
    internal constructor(file: File, forTesting: Boolean = true) : this(file)
    private val mutex = Mutex()
    @Volatile private var policy = HistoryPolicy()

    suspend fun add(entry: TranscriptEntry) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val arr = readArray()
            arr.put(entry.toJson())
            while (arr.length() > policy.maxEntries) arr.remove(0)
            if (policy.retainHistory) atomicWrite(arr.toString()) else {
                file.delete()
                backup.delete()
            }
        }
    }

    suspend fun getAll(): List<TranscriptEntry> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val arr = readArray()
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                TranscriptEntry(
                    timestamp = obj.optLong("timestamp", 0L),
                    originalText = obj.optString("originalText", ""),
                    translatedText = obj.optString("translatedText", ""),
                    sourceLanguage = obj.optString("sourceLanguage", ""),
                    targetLanguage = obj.optString("targetLanguage", ""),
                    id = obj.optString("id", legacyId(obj))
                )
            }.reversed() // newest first
        }
    }

    suspend fun delete(timestamp: Long): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val arr = readArray()
            val before = arr.length()
            val result = JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optLong("timestamp", 0L) != timestamp) {
                    result.put(obj)
                }
            }
            if (result.length() == before) return@withContext false
            atomicWrite(result.toString())
            true
        }
    }

    suspend fun deleteById(id: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val arr = readArray()
            val result = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("id", legacyId(obj)) != id) result.put(obj)
        }
            if (result.length() == arr.length()) return@withContext false
            atomicWrite(result.toString())
            true
        }
    }

    suspend fun setPolicy(newPolicy: HistoryPolicy) = mutex.withLock {
        policy = newPolicy
        withContext(Dispatchers.IO) {
            if (!newPolicy.retainHistory) {
                file.delete()
                backup.delete()
            }
            else {
                val arr = readArray()
                while (arr.length() > newPolicy.maxEntries) arr.remove(0)
                atomicWrite(arr.toString())
            }
        }
    }

    suspend fun export(): TranscriptExport = TranscriptExport(getAll())

    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) { file.delete(); backup.delete() }
    }

    private fun readArray(): JSONArray {
        return try {
            if (file.exists()) JSONArray(file.readText()) else JSONArray()
        } catch (_: Throwable) {
            try { if (backup.exists()) JSONArray(backup.readText()) else JSONArray() } catch (_: Throwable) { JSONArray() }
        }
    }

    private fun atomicWrite(contents: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temp).use { stream -> stream.write(contents.toByteArray(Charsets.UTF_8)); stream.fd.sync() }
        if (file.exists()) Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun legacyId(obj: JSONObject): String = "legacy-${obj.optLong("timestamp", 0L)}-${obj.optString("originalText").hashCode()}"

    private val backup get() = File(file.parentFile, "${file.name}.bak")

    private fun TranscriptEntry.toJson() = JSONObject().apply {
        put("id", id)
        put("timestamp", timestamp)
        put("originalText", originalText)
        put("translatedText", translatedText)
        put("sourceLanguage", sourceLanguage)
        put("targetLanguage", targetLanguage)
    }

    companion object {
        private const val MAX_ENTRIES = 500
    }
}

data class HistoryPolicy(val maxEntries: Int = 500, val retainHistory: Boolean = true) {
    init { require(maxEntries > 0) }
}
