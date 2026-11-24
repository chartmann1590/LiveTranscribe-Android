package com.charles.livecaptionn

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.charles.livecaptionn.speech.LocalVoskSttClient
import com.charles.livecaptionn.speech.ModelQuality
import com.charles.livecaptionn.speech.VoskModelCatalog
import com.charles.livecaptionn.speech.VoskModelRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full per-language verification of every Vosk language the app offers:
 * for each unique language in [VoskModelCatalog] —
 *   1. bundled models (en, vi) must load from APK assets and run a
 *      silence transcription without error,
 *   2. the smallest downloadable variant must download through the app's
 *      own [VoskModelRegistry.downloadAndInstall] (this is the exact path
 *      the in-app "Get" button uses), load via [LocalVoskSttClient], and
 *      transcribe 1 s of silence without error (empty text is expected and
 *      fine — this proves model integrity + recognizer init, the path that
 *      crashed in issue #221).
 * Each downloaded model is uninstalled after its check so the sweep fits
 * on a nearly-full device. Failures are collected and asserted at the end.
 */
@RunWith(AndroidJUnit4::class)
class VoskAllLanguagesTest {

    @Test
    fun everyVoskLanguage_downloadsLoadsAndRuns() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val registry = VoskModelRegistry(context)
        registry.refresh()
        delay(2_000)
        registry.refresh()
        val client = LocalVoskSttClient(registry)
        val failures = mutableListOf<String>()
        val silence = ByteArray(32_000) // 1 s of 16 kHz 16-bit silence

        // 1) Bundled models load straight from assets. The assets/models tree
        // is gitignored (models are staged before release builds), so a build
        // without staged assets has no bundled copy — in that case download
        // the identical catalog model and verify that instead, and say so.
        for (bundled in VoskModelCatalog.BUNDLED_MODELS) {
            registry.refresh()
            val isBundledInstalled = registry.installedModels()
                .any { it.modelName == bundled.modelName && it.isBundled }
            if (!isBundledInstalled) {
                val entry = VoskModelCatalog.findByModelName(bundled.modelName)
                val dl = entry?.let {
                    withTimeoutOrNull(600_000L) { registry.downloadAndInstall(it) }
                }
                registry.refresh()
                val outcome = withTimeoutOrNull(300_000L) {
                    client.transcribe(silence, 16_000, bundled.languageCode)
                }
                val ok = dl == true && outcome != null && outcome.errorMessage == null
                Log.i(TAG, "VOSKX bundled ${bundled.languageCode} model=${bundled.modelName} ok=$ok note='assets not staged in this build; verified via download of identical model' text='${outcome?.text}' err=${outcome?.errorMessage}")
                if (!ok) failures += "${bundled.languageCode} bundled-assets-absent, download fallback: ${outcome?.errorMessage ?: "download failed/timeout"}"
                if (entry != null) registry.uninstall(entry.modelName)
                continue
            }
            val outcome = withTimeoutOrNull(120_000L) {
                client.transcribe(silence, 16_000, bundled.languageCode)
            }
            val ok = outcome != null && outcome.errorMessage == null
            Log.i(TAG, "VOSKX bundled ${bundled.languageCode} model=${bundled.modelName} ok=$ok text='${outcome?.text}' err=${outcome?.errorMessage}")
            if (!ok) failures += "${bundled.languageCode} bundled: ${outcome?.errorMessage ?: "timeout"}"
        }

        // 2) Smallest downloadable variant per unique language. CI runs can
        // pass -e voskLanguages es,de,ru,zh to scope downloads to a subset;
        // on-device runs leave it unset and cover every language.
        val only = InstrumentationRegistry.getArguments().getString("voskLanguages")
            ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.toSet()
        val smallest = VoskModelCatalog.DOWNLOADABLE
            .groupBy { it.languageCode.lowercase() }
            .mapValues { (_, entries) -> entries.minByOrNull { it.sizeMb }!! }
            .filterKeys { only == null || it in only }
            .toSortedMap()
        Log.i(TAG, "VOSKX languages=${smallest.keys} subset=${only != null}")

        for ((code, entry) in smallest) {
            // en/vi small entries are byte-identical to the bundled models
            // already proven above; don't re-download them.
            if (VoskModelCatalog.BUNDLED_MODELS.any { it.modelName == entry.modelName }) {
                Log.i(TAG, "VOSKX $code skip-download (same model as bundled, already verified) model=${entry.modelName}")
                continue
            }
            val started = System.currentTimeMillis()
            val installed = withTimeoutOrNull(1_800_000L) {
                registry.downloadAndInstall(entry)
            }
            if (installed != true) {
                val err = registry.lastError(entry.modelName) ?: "timeout/null"
                Log.i(TAG, "VOSKX $code download ok=false model=${entry.modelName} err=$err")
                failures += "$code download: $err"
                continue
            }
            registry.refresh()
            val outcome = withTimeoutOrNull(300_000L) {
                client.transcribe(silence, 16_000, code)
            }
            val ok = outcome != null && outcome.errorMessage == null
            val ms = System.currentTimeMillis() - started
            Log.i(TAG, "VOSKX $code ok=$ok ms=$ms model=${entry.modelName} sizeMb=${entry.sizeMb} quality=${entry.quality} text='${outcome?.text}' err=${outcome?.errorMessage}")
            if (!ok) failures += "$code load/transcribe: ${outcome?.errorMessage ?: "timeout"}"
            registry.uninstall(entry.modelName)
            registry.refresh()
        }
        Log.i(TAG, "VOSKX_DONE languages=${smallest.size} failed=${failures.size} failures=$failures")
        assertTrue("Vosk languages that failed: $failures", failures.isEmpty())
    }

    private companion object {
        const val TAG = "VoskAllLang"
    }
}
