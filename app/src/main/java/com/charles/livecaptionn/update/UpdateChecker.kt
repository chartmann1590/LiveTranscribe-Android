package com.charles.livecaptionn.update

import android.util.Log
import com.charles.livecaptionn.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Polls the GitHub Releases API for a new version of the app and exposes the
 * result as a [StateFlow]. Thread-safe: [check] can be called from any scope.
 *
 * Update detection:
 *   - Parses `tag_name` from `/releases/latest`.
 *   - Expects the CI tag format `v1.0.<buildNumber>` set by `.github/workflows/build.yml`.
 *   - If `<buildNumber>` > [BuildConfig.VERSION_CODE], an [UpdateInfo] is emitted.
 *
 * Tags that don't match the format are ignored, so manually tagged semver
 * releases won't accidentally trigger false positives. Dev builds (versionCode = 1)
 * will see any `v1.0.N` tag where N > 1.
 */
class UpdateChecker {

    private val mutable = MutableStateFlow<UpdateInfo?>(null)
    val available: StateFlow<UpdateInfo?> = mutable.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches /releases/latest. Returns the [UpdateInfo] when a newer build is
     * found (and pushes it into [available]), or null otherwise.
     */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO_OWNER}/" +
            "${BuildConfig.UPDATE_REPO_NAME}/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "LiveCaptionN-Android/${BuildConfig.VERSION_NAME}")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Update check HTTP ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string().orEmpty()
                val parsed = parseRelease(body) ?: return@withContext null
                if (parsed.buildNumber > BuildConfig.VERSION_CODE) {
                    mutable.value = parsed
                    parsed
                } else {
                    // Keep prior value cleared so dismissed banners stay dismissed.
                    mutable.value = null
                    null
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Update check failed", t)
            null
        }
    }

    /** Clears any currently-surfaced update (used when the user dismisses the banner). */
    fun dismiss() {
        mutable.value = null
    }

    private fun parseRelease(json: String): UpdateInfo? {
        return try {
            val obj = JSONObject(json)
            val tag = obj.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
            val buildNumber = parseBuildNumber(tag) ?: return null
            val name = obj.optString("name").ifBlank { "LiveCaptionN $tag" }
            val notes = obj.optString("body").trim()
            val htmlUrl = obj.optString("html_url")
                .ifBlank { "https://github.com/${BuildConfig.UPDATE_REPO_OWNER}/${BuildConfig.UPDATE_REPO_NAME}/releases/latest" }

            val apkUrl = obj.optJSONArray("assets")?.let { assets ->
                var best: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name")
                    val download = asset.optString("browser_download_url")
                    if (download.isBlank() || !assetName.endsWith(".apk", ignoreCase = true)) continue
                    // Prefer the signed release APK; fall back to debug/unsigned otherwise.
                    if (assetName.contains("release", ignoreCase = true)) return@let download
                    if (best == null) best = download
                }
                best
            }

            UpdateInfo(
                tagName = tag,
                releaseName = name,
                buildNumber = buildNumber,
                apkDownloadUrl = apkUrl,
                releasePageUrl = htmlUrl,
                notes = notes
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to parse release json", t)
            null
        }
    }

    companion object {
        private const val TAG = "UpdateChecker"

        /** Extracts `42` from `v1.0.42`. Returns null if the tag doesn't match. */
        internal fun parseBuildNumber(tag: String): Int? {
            val match = TAG_REGEX.matchEntire(tag.trim()) ?: return null
            return match.groupValues[1].toIntOrNull()
        }

        private val TAG_REGEX = Regex("""v?1\.0\.(\d+)""")
    }
}
