package com.charles.livecaptionn.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class GlossaryEntry(val id: String = newTranscriptId(), val phrase: String, val replacement: String, val caseSensitive: Boolean = false)

fun applyGlossary(text: String, entries: List<GlossaryEntry>): String = entries.filter { it.phrase.isNotEmpty() }.fold(text) { result, e ->
    if (e.caseSensitive) result.replace(e.phrase, e.replacement) else result.replace(Regex(Regex.escape(e.phrase), RegexOption.IGNORE_CASE), e.replacement)
}

interface GlossaryRepository { suspend fun list(): List<GlossaryEntry>; suspend fun save(entry: GlossaryEntry); suspend fun delete(id: String): Boolean }

class FileGlossaryRepository private constructor(private val file: File) : GlossaryRepository {
    constructor(context: Context) : this(File(context.filesDir, "caption_glossary.json"))
    internal constructor(file: File, forTesting: Boolean = true) : this(file)
    override suspend fun list() = withContext(Dispatchers.IO) { read().map(::fromJson) }
    override suspend fun save(entry: GlossaryEntry) = withContext(Dispatchers.IO) { val next = read().map(::fromJson).filterNot { it.id == entry.id } + entry; file.parentFile?.mkdirs(); file.writeText(JSONArray(next.map(::toJson)).toString()) }
    override suspend fun delete(id: String) = withContext(Dispatchers.IO) { val old = read().map(::fromJson); val next = old.filterNot { it.id == id }; if (old.size == next.size) false else { file.writeText(JSONArray(next.map(::toJson)).toString()); true } }
    private fun read() = try { if (file.exists()) { val a = JSONArray(file.readText()); (0 until a.length()).map(a::getJSONObject) } else emptyList() } catch (_: Throwable) { emptyList() }
    private fun toJson(e: GlossaryEntry) = JSONObject().put("id", e.id).put("phrase", e.phrase).put("replacement", e.replacement).put("caseSensitive", e.caseSensitive)
    private fun fromJson(o: JSONObject) = GlossaryEntry(o.optString("id", newTranscriptId()), o.optString("phrase"), o.optString("replacement"), o.optBoolean("caseSensitive", false))
}
