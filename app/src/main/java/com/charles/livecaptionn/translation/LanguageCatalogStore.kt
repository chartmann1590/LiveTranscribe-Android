package com.charles.livecaptionn.translation

import android.util.Log
import com.charles.livecaptionn.data.SettingsRepository
import com.charles.livecaptionn.settings.CaptionSettings
import com.charles.livecaptionn.settings.Language
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

data class LibreCatalogState(
    val languages: List<Language> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val lastBaseUrl: String = ""
) {
    /** What the UI should offer when picking source/target languages. */
    fun effective(): List<Language> =
        if (languages.isNotEmpty()) languages else Language.FALLBACK
}

/**
 * Caches the `/languages` response from the configured LibreTranslate server
 * and re-fetches whenever the user changes the server URL. Everything lives
 * in a StateFlow so the Compose UI can react.
 */
class LanguageCatalogStore(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope
) {
    private val mutable = MutableStateFlow(LibreCatalogState())
    val state: StateFlow<LibreCatalogState> = mutable.asStateFlow()

    private var refreshJob: Job? = null

    init {
        scope.launch {
            settingsRepository.settingsFlow
                .map { it.serverBaseUrl.ifBlank { CaptionSettings.DEFAULT_BASE_URL } }
                .distinctUntilChanged()
                .collect { url -> refreshIfNeeded(url) }
        }
    }

    fun refresh() {
        val url = mutable.value.lastBaseUrl.ifBlank { CaptionSettings.DEFAULT_BASE_URL }
        refreshIfNeeded(url, force = true)
    }

    private fun refreshIfNeeded(baseUrl: String, force: Boolean = false) {
        val current = mutable.value
        if (!force && current.lastBaseUrl == baseUrl && current.languages.isNotEmpty()) return
        refreshJob?.cancel()
        refreshJob = scope.launch(Dispatchers.IO) {
            mutable.value = current.copy(loading = true, error = null, lastBaseUrl = baseUrl)
            try {
                val api = buildApi(baseUrl)
                val fetched = api.languages()
                val mapped = fetched
                    .map { Language(code = it.code, name = it.name) }
                    .sortedBy { it.name.lowercase() }
                mutable.value = LibreCatalogState(
                    languages = mapped,
                    loading = false,
                    error = null,
                    lastBaseUrl = baseUrl
                )
                Log.d("LanguageCatalog", "Loaded ${mapped.size} languages from $baseUrl")
            } catch (t: Throwable) {
                Log.w("LanguageCatalog", "Failed to load languages from $baseUrl", t)
                mutable.value = LibreCatalogState(
                    languages = emptyList(),
                    loading = false,
                    error = t.message ?: t::class.java.simpleName,
                    lastBaseUrl = baseUrl
                )
            }
        }
    }

    private fun buildApi(baseUrl: String): LibreTranslateApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val client = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LibreTranslateApi::class.java)
    }
}
