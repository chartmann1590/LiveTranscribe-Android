package com.charles.livecaptionn.translation

import android.util.Log
import com.charles.livecaptionn.BuildConfig
import com.charles.livecaptionn.data.SettingsRepository
import com.charles.livecaptionn.settings.CaptionSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class LibreTranslateRepository(
    private val settingsRepository: SettingsRepository
) : TranslationRepository {

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var cachedApi: LibreTranslateApi? = null

    private fun createApi(baseUrl: String): LibreTranslateApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val client = OkHttpClient.Builder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(9, TimeUnit.SECONDS)
            .writeTimeout(9, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
            }
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LibreTranslateApi::class.java)
    }

    private suspend fun api(): LibreTranslateApi {
        val settings = settingsRepository.settingsFlow.first()
        val url = settings.serverBaseUrl.ifBlank { CaptionSettings.DEFAULT_BASE_URL }
        val existing = cachedApi
        if (existing != null && cachedBaseUrl == url) return existing
        return synchronized(this) {
            if (cachedApi == null || cachedBaseUrl != url) {
                cachedApi = createApi(url)
                cachedBaseUrl = url
            }
            cachedApi!!
        }
    }

    override suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String {
        val clean = text.trim()
        if (clean.isEmpty()) return clean
        if (targetCode.isBlank()) return clean
        return try {
            val req = TranslateRequest(
                q = clean,
                source = if (autoDetect || sourceCode.isBlank()) "auto" else sourceCode,
                target = targetCode
            )
            api().translate(req).translatedText.ifBlank { clean }
        } catch (t: Throwable) {
            Log.w("LibreTranslateRepo", "Translation failed; returning original text.", t)
            clean
        }
    }
}
