package com.charles.livecaptionn.speech

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Keeps track of which Vosk models exist on disk right now and lets the UI
 * kick off downloads of additional ones. A model is "installed" if either:
 *
 *   - its folder lives inside `assets/models/<name>/` (shipped with the app), OR
 *   - it has been downloaded into `filesDir/vosk/models/<name>/`.
 *
 * The public [models] StateFlow is what the settings screen binds against;
 * call [refresh] after a download so the list reflects the new state.
 */
class VoskModelRegistry(private val context: Context) {

    private val mutableModels = MutableStateFlow<List<VoskModelInfo>>(emptyList())
    val models: StateFlow<List<VoskModelInfo>> = mutableModels.asStateFlow()

    /** Tracks in-progress downloads (0f..1f). Entries are removed when done. */
    private val progressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = progressMap.asStateFlow()

    /** Last download error keyed by model name, for surfacing in the UI. */
    private val errorMap = ConcurrentHashMap<String, String>()

    init {
        refresh()
    }

    fun refresh() {
        val bundledNames = VoskModelCatalog.BUNDLED_MODELS.mapNotNull { b ->
            if (assetModelExists(b.modelName)) b else null
        }
        val downloadedNames = listDownloadedModelNames()

        val known = buildList {
            // Bundled first — mark them as installed + bundled.
            bundledNames.forEach { b ->
                val catalog = VoskModelCatalog.findByModelName(b.modelName)
                    ?: VoskModelCatalog.findByLanguage(b.languageCode)
                add(
                    VoskModelInfo(
                        languageCode = b.languageCode,
                        languageName = catalog?.languageName ?: b.languageCode.uppercase(),
                        modelName = b.modelName,
                        sizeMb = catalog?.sizeMb ?: 0,
                        downloadUrl = null,
                        installed = true,
                        source = VoskSource.BUNDLED,
                        isBundled = true
                    )
                )
            }
            // Downloadable entries. Mark installed when the folder exists under filesDir.
            VoskModelCatalog.DOWNLOADABLE.forEach { entry ->
                if (bundledNames.any { it.modelName == entry.modelName }) return@forEach
                val installed = downloadedNames.contains(entry.modelName)
                add(entry.copy(installed = installed, source = VoskSource.DOWNLOADED))
            }
        }
        mutableModels.value = known
    }

    fun installedLanguageCodes(): List<String> =
        mutableModels.value.filter { it.installed }.map { it.languageCode }.distinct()

    fun installedModels(): List<VoskModelInfo> =
        mutableModels.value.filter { it.installed }

    /** Resolves the on-disk directory for a language code, or null if not installed.
     *  When both small and large variants are installed for the same language,
     *  the large one wins since the user explicitly opted into it. */
    fun resolveModelDir(languageCode: String): File? {
        val installed = mutableModels.value.filter {
            it.languageCode.equals(languageCode, ignoreCase = true) && it.installed
        }
        val candidate = installed.firstOrNull { it.quality == ModelQuality.LARGE }
            ?: installed.firstOrNull()
            ?: return null

        return when (candidate.source) {
            VoskSource.BUNDLED -> ensureBundledCopied(candidate.modelName)
            VoskSource.DOWNLOADED -> downloadedModelDir(candidate.modelName)
        }
    }

    /**
     * Downloads [info] to local storage, unzips it, and refreshes state. Returns
     * true on success. The caller should observe [downloadProgress] for UI feedback.
     */
    suspend fun downloadAndInstall(info: VoskModelInfo): Boolean = withContext(Dispatchers.IO) {
        val url = info.downloadUrl
        if (url.isNullOrBlank()) {
            errorMap[info.modelName] = "No download URL"
            return@withContext false
        }
        errorMap.remove(info.modelName)
        reportProgress(info.modelName, 0f)
        try {
            val cacheDir = File(context.cacheDir, "vosk-downloads").apply { mkdirs() }
            val zipFile = File(cacheDir, "${info.modelName}.zip")
            httpClient().newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    errorMap[info.modelName] = "HTTP ${resp.code}"
                    reportProgress(info.modelName, null)
                    return@withContext false
                }
                val body = resp.body ?: run {
                    errorMap[info.modelName] = "Empty response"
                    reportProgress(info.modelName, null)
                    return@withContext false
                }
                val total = body.contentLength().coerceAtLeast(1)
                body.byteStream().use { input ->
                    FileOutputStream(zipFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            reportProgress(info.modelName, (downloaded.toFloat() / total).coerceIn(0f, 0.9f))
                        }
                    }
                }
            }

            reportProgress(info.modelName, 0.92f)
            val targetRoot = File(context.filesDir, "vosk/models").apply { mkdirs() }
            unzipInto(zipFile, targetRoot, info.modelName)
            zipFile.delete()
            reportProgress(info.modelName, null)
            refresh()
            true
        } catch (t: Throwable) {
            Log.w("VoskModelRegistry", "Failed to install ${info.modelName}", t)
            errorMap[info.modelName] = t.message ?: t::class.java.simpleName
            reportProgress(info.modelName, null)
            false
        }
    }

    /** Deletes a downloaded model from disk. Bundled models cannot be deleted. */
    suspend fun uninstall(modelName: String): Boolean = withContext(Dispatchers.IO) {
        val bundled = VoskModelCatalog.BUNDLED_MODELS.any { it.modelName == modelName }
        if (bundled) return@withContext false
        val dir = downloadedModelDir(modelName)
        val ok = if (dir.exists()) dir.deleteRecursively() else true
        if (ok) refresh()
        ok
    }

    fun lastError(modelName: String): String? = errorMap[modelName]

    // ── internals ──────────────────────────────────────────────────────────

    private fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun reportProgress(modelName: String, progress: Float?) {
        val current = progressMap.value.toMutableMap()
        if (progress == null) current.remove(modelName) else current[modelName] = progress
        progressMap.value = current
    }

    private fun assetModelExists(modelName: String): Boolean {
        return try {
            !context.assets.list("models/$modelName").isNullOrEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    private fun listDownloadedModelNames(): Set<String> {
        val root = File(context.filesDir, "vosk/models")
        if (!root.exists() || !root.isDirectory) return emptySet()
        return root.listFiles()
            ?.filter { it.isDirectory && File(it, ".installed").exists() }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()
    }

    private fun downloadedModelDir(modelName: String): File {
        val root = File(context.filesDir, "vosk/models").apply { mkdirs() }
        return File(root, modelName)
    }

    private fun ensureBundledCopied(modelName: String): File {
        val targetDir = File(context.filesDir, "vosk/bundled/$modelName")
        val marker = File(targetDir, ".copied")
        if (!marker.exists()) {
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()
            copyAssetDirectory("models/$modelName", targetDir)
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
            val childAsset = "$assetPath/$child"
            val childTarget = File(targetDir, child)
            if (context.assets.list(childAsset).isNullOrEmpty()) {
                context.assets.open(childAsset).use { input ->
                    childTarget.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                copyAssetDirectory(childAsset, childTarget)
            }
        }
    }

    /**
     * Vosk zips are packaged as `{modelName}/...`. We extract into a temp dir first,
     * then move the inner folder to [targetRoot]/[expectedName] and drop an
     * `.installed` marker so [refresh] can see it.
     */
    private fun unzipInto(zipFile: File, targetRoot: File, expectedName: String) {
        val finalDir = File(targetRoot, expectedName)
        if (finalDir.exists()) finalDir.deleteRecursively()
        val stagingDir = File(targetRoot, ".staging-$expectedName").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val outFile = File(stagingDir, entry.name)
                // Path traversal guard
                if (!outFile.canonicalPath.startsWith(stagingDir.canonicalPath)) continue
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { zis.copyTo(it) }
                }
                zis.closeEntry()
            }
        }
        // Many Vosk zips wrap everything under a single top-level dir. Collapse it.
        val top = stagingDir.listFiles()?.singleOrNull { it.isDirectory }
        val sourceDir = top ?: stagingDir
        finalDir.parentFile?.mkdirs()
        if (!sourceDir.renameTo(finalDir)) {
            sourceDir.copyRecursively(finalDir, overwrite = true)
            sourceDir.deleteRecursively()
        }
        stagingDir.deleteRecursively()
        File(finalDir, ".installed").writeText("ok")
    }
}
