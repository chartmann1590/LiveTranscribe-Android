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
import java.net.ConnectException
import java.net.URL
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class TranscribeOutcome(
    val text: String,
    /** Non-null when the HTTP request failed or the response was unusable (not "no speech"). */
    val errorMessage: String? = null
)

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

    suspend fun transcribe(wavData: ByteArray, sttUrl: String, languageCode: String?): TranscribeOutcome =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = sttUrl.trim()
                if (cleanUrl.isBlank()) {
                    return@withContext TranscribeOutcome("", "Speech-to-text URL is empty")
                }
                val separator = if ("?" in cleanUrl) "&" else "?"
                val languageParam = languageCode?.takeIf { it.isNotBlank() }?.let { "&language=$it" }.orEmpty()
                val url = "${cleanUrl}${separator}task=transcribe$languageParam"
                Log.d("WhisperSttClient", "POST $url")

                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "audio_file", "audio.wav",
                        wavData.toRequestBody("audio/wav".toMediaType())
                    )
                    .build()

                val request = Request.Builder().url(url).post(body).build()

                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@use TranscribeOutcome(
                            "",
                            "Speech server error HTTP ${response.code}"
                        )
                    }
                    try {
                        TranscribeOutcome(JSONObject(bodyStr).optString("text", "").trim())
                    } catch (_: Exception) {
                        TranscribeOutcome("", "Speech server returned invalid response")
                    }
                }
            } catch (t: Throwable) {
                Log.w("WhisperSttClient", "STT request failed", t)
                TranscribeOutcome("", sttFailureMessage(sttUrl, t))
            }
        }

    private fun sttFailureMessage(sttUrl: String, t: Throwable): String {
        val host = runCatching { URL(sttUrl.trim()).host.lowercase() }.getOrNull()
        return when (t) {
            is SocketTimeoutException -> "Speech server timed out (check URL and network)"
            is UnknownHostException -> "Speech server host not found"
            is ConnectException -> {
                if (host == "localhost" || host == "127.0.0.1" || host == "::1") {
                    "Cannot connect to speech server at $host. On a phone, run adb reverse tcp:9000 tcp:9000 or use your computer's network IP."
                } else {
                    "Cannot connect to speech server"
                }
            }
            else -> "Speech request failed"
        }
    }
}
