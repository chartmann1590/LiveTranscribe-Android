package com.charles.livecaptionn.speech

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Sends WAV audio to a Whisper-compatible STT endpoint and returns transcribed text.
 *
 * Supports whisper-asr-webservice format:
 *   POST {baseUrl}  (e.g. http://host:9000/asr?output=json)
 *   multipart: audio_file = audio.wav
 *   response: {"text": "..."}
 */
class WhisperSttClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(wavData: ByteArray, sttUrl: String, languageCode: String): String =
        withContext(Dispatchers.IO) {
            try {
                val separator = if ("?" in sttUrl) "&" else "?"
                val url = "${sttUrl}${separator}task=transcribe&language=$languageCode"

                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "audio_file", "audio.wav",
                        wavData.toRequestBody("audio/wav".toMediaType())
                    )
                    .build()

                val request = Request.Builder().url(url).post(body).build()

                client.newCall(request).execute().use { response ->
                    val json = response.body?.string() ?: return@use ""
                    JSONObject(json).optString("text", "").trim()
                }
            } catch (t: Throwable) {
                Log.w("WhisperSttClient", "STT request failed", t)
                ""
            }
        }
}
