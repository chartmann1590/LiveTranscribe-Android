package com.charles.livecaptionn.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CaptionProfile(
    val id: String = newTranscriptId(), val name: String, val sourceLanguage: String,
    val targetLanguage: String, val textSizeSp: Float = 20f, val showOriginal: Boolean = true,
    val proOnly: Boolean = false
)

interface CaptionProfileRepository {
    suspend fun list(): List<CaptionProfile>
    suspend fun save(profile: CaptionProfile)
    suspend fun delete(id: String): Boolean
}

class FileCaptionProfileRepository private constructor(private val file: File) : CaptionProfileRepository {
    constructor(context: Context) : this(File(context.filesDir, "caption_profiles.json"))
    internal constructor(file: File, forTesting: Boolean = true) : this(file)
    override suspend fun list() = withContext(Dispatchers.IO) { read().map(::fromJson) }
    override suspend fun save(profile: CaptionProfile) = withContext(Dispatchers.IO) {
        val profiles = read().map(::fromJson).filterNot { it.id == profile.id } + profile
        file.parentFile?.mkdirs(); file.writeText(JSONArray(profiles.map(::toJson)).toString())
    }
    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val profiles = read().map(::fromJson); val next = profiles.filterNot { it.id == id }
        if (next.size == profiles.size) false else { file.writeText(JSONArray(next.map(::toJson)).toString()); true }
    }
    private fun read() = try { if (file.exists()) { val a = JSONArray(file.readText()); (0 until a.length()).map(a::getJSONObject) } else emptyList() } catch (_: Throwable) { emptyList() }
    private fun toJson(p: CaptionProfile) = JSONObject().put("id", p.id).put("name", p.name).put("source", p.sourceLanguage).put("target", p.targetLanguage).put("size", p.textSizeSp).put("original", p.showOriginal).put("pro", p.proOnly)
    private fun fromJson(o: JSONObject) = CaptionProfile(o.optString("id", newTranscriptId()), o.optString("name"), o.optString("source"), o.optString("target"), o.optDouble("size", 20.0).toFloat(), o.optBoolean("original", true), o.optBoolean("pro", false))
}
