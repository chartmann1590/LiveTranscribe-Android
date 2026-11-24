package com.charles.livecaptionn

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.charles.livecaptionn.translation.MlKitLanguages
import com.charles.livecaptionn.translation.MlKitTranslationRepository
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Full per-language verification of every ML Kit language the app offers:
 * for each non-English language X —
 *   1. the en model and the X model must download (requireModel),
 *   2. en -> X translation of a known sentence must be non-blank and differ
 *      from the English input,
 *   3. X -> en translation of a native greeting must be non-blank, differ
 *      from the input, and contain Latin letters (i.e. real English output).
 * The X model is deleted after each language so the whole sweep fits on a
 * nearly-full device, and the pair's Translator clients are released after
 * each language — cached translators hold native model resources for the
 * life of the process, and accumulating ~116 of them on the CI emulator
 * (512 MB heap) preceded a process crash that killed the whole sweep
 * (Language Verification run #5, 2026-11-24). Failures are collected,
 * logged per language, and only asserted at the end so one bad language
 * never hides the rest.
 *
 * CI runs this test in chunks: pass
 * `-e mlkitLanguages af,ar,be,...` to scope one invocation to a subset of
 * languages (same pattern as VoskAllLanguagesTest's `voskLanguages`), so a
 * crash in one chunk cannot take the remaining languages' results with it.
 * With no argument the full sweep runs, as on a local device.
 */
@RunWith(AndroidJUnit4::class)
class AllLanguagesVerificationTest {

    @Test
    fun everyLanguage_downloadsAndTranslatesBothDirections() = runBlocking {
        val repo = MlKitTranslationRepository()
        val failures = mutableListOf<String>()
        val allCodes = MlKitLanguages.LIST.map { it.code }.filter { it != "en" }
        // `-e mlkitLanguages af,ar,...` scopes this run to one chunk (CI);
        // unknown codes are reported as failures rather than silently skipped.
        val only = InstrumentationRegistry.getArguments().getString("mlkitLanguages")
            ?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }
        val codes = if (only.isNullOrEmpty()) {
            allCodes
        } else {
            only.filter { code ->
                val known = code in allCodes
                if (!known) failures += "$code: not in MlKitLanguages.LIST (chunk argument)"
                known
            }
        }
        Log.i(TAG, "MLKITX_START chunk=${only?.joinToString(",") ?: "all"} count=${codes.size}")
        try {
            for (code in codes) {
                val sample = SAMPLES[code]
                if (sample == null) {
                    failures += "$code: no native sample in test table"
                    Log.i(TAG, "MLKITX $code ok=false why=no-sample")
                    continue
                }
                val started = System.currentTimeMillis()

                val enToX = withTimeoutOrNull(240_000L) {
                    runCatching {
                        repo.requireModel("en", code)
                        repo.translate(EN_SENTENCE, "en", code, autoDetect = false)
                    }
                }
                val outX = enToX?.getOrNull().orEmpty()
                val okEnToX = enToX != null && enToX.isSuccess &&
                    outX.isNotBlank() && !outX.equals(EN_SENTENCE, ignoreCase = true)
                val whyEnToX = when {
                    enToX == null -> "timeout"
                    enToX.isFailure -> enToX.exceptionOrNull().toString()
                    !okEnToX -> "blank-or-identity output"
                    else -> ""
                }
                Log.i(TAG, "MLKITX en->$code ok=$okEnToX out=$outX why=$whyEnToX")
                if (!okEnToX) failures += "$code en->X: $whyEnToX"

                val xToEn = if (okEnToX) withTimeoutOrNull(240_000L) {
                    runCatching { repo.translate(sample, code, "en", autoDetect = false) }
                } else null
                val outEn = xToEn?.getOrNull().orEmpty()
                val okXToEn = xToEn != null && xToEn.isSuccess &&
                    outEn.isNotBlank() && !outEn.equals(sample, ignoreCase = true) &&
                    Regex("[A-Za-z]").containsMatchIn(outEn)
                val whyXToEn = when {
                    !okEnToX -> "skipped (en->X failed)"
                    xToEn == null -> "timeout"
                    xToEn.isFailure -> xToEn.exceptionOrNull().toString()
                    !okXToEn -> "blank, identity, or non-Latin output"
                    else -> ""
                }
                val ms = System.currentTimeMillis() - started
                Log.i(TAG, "MLKITX $code->en ok=$okXToEn ms=$ms in=$sample out=$outEn why=$whyXToEn")
                if (!okXToEn) failures += "$code X->en: $whyXToEn"

                // Free this pair's native translator resources before the
                // next language — see the class KDoc (run #5 crash).
                repo.releasePair("en", code)
                deleteModel(code)
            }
        } finally {
            repo.close()
            deleteModel("en")
        }
        Log.i(TAG, "MLKITX_DONE tested=${codes.size} failed=${failures.size} failures=$failures")
        assertTrue("Languages that failed: $failures", failures.isEmpty())
    }

    private suspend fun deleteModel(code: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val model = TranslateRemoteModel.Builder(code).build()
                Tasks.await(RemoteModelManager.getInstance().deleteDownloadedModel(model), 60, TimeUnit.SECONDS)
            }
        }
    }

    private companion object {
        const val TAG = "AllLangVerify"
        const val EN_SENTENCE = "Hello, this is a test of live captions."

        /** "Hello, how are you today?" (or close greeting) per language. */
        val SAMPLES: Map<String, String> = mapOf(
            "af" to "Hallo, hoe gaan dit vandag?",
            "ar" to "مرحبا، كيف حالك اليوم؟",
            "be" to "Прывітанне, як ты сёння?",
            "bg" to "Здравейте, как сте днес?",
            "bn" to "হ্যালো, আজ কেমন আছেন?",
            "ca" to "Hola, com estàs avui?",
            "cs" to "Ahoj, jak se máš dnes?",
            "cy" to "Helo, sut wyt ti heddiw?",
            "da" to "Hej, hvordan har du det i dag?",
            "de" to "Hallo, wie geht es dir heute?",
            "el" to "Γεια σας, πώς είστε σήμερα;",
            "eo" to "Saluton, kiel vi fartas hodiaŭ?",
            "es" to "Hola, ¿cómo estás hoy?",
            "et" to "Tere, kuidas sul täna läheb?",
            "fa" to "سلام، امروز چطوری؟",
            "fi" to "Hei, miten voit tänään?",
            "fr" to "Bonjour, comment allez-vous aujourd'hui ?",
            "ga" to "Dia duit, conas atá tú inniu?",
            "gl" to "Ola, como estás hoxe?",
            "gu" to "નમસ્તે, આજે તમે કેમ છો?",
            "he" to "שלום, מה שלומך היום?",
            "hi" to "नमस्ते, आज आप कैसे हैं?",
            "hr" to "Bok, kako si danas?",
            "ht" to "Bonjou, kijan ou ye jodi a?",
            "hu" to "Szia, hogy vagy ma?",
            "id" to "Halo, apa kabar hari ini?",
            "is" to "Halló, hvernig hefurðu það í dag?",
            "it" to "Ciao, come stai oggi?",
            "ja" to "こんにちは、今日は元気ですか？",
            "ka" to "გამარჯობა, როგორ ხარ დღეს?",
            "kn" to "ಹಲೋ, ಇಂದು ಹೇಗಿದ್ದೀರಿ?",
            "ko" to "안녕하세요, 오늘 어떠세요?",
            "lt" to "Labas, kaip sekasi šiandien?",
            "lv" to "Sveiki, kā tev šodien klājas?",
            "mk" to "Здраво, како си денес?",
            "mr" to "नमस्कार, आज तुम्ही कसे आहात?",
            "ms" to "Halo, apa khabar hari ini?",
            "mt" to "Bongu, kif int illum?",
            "nl" to "Hallo, hoe gaat het vandaag?",
            "no" to "Hei, hvordan har du det i dag?",
            "pl" to "Cześć, jak się masz dzisiaj?",
            "pt" to "Olá, como você está hoje?",
            "ro" to "Bună, ce faci azi?",
            "ru" to "Привет, как дела сегодня?",
            "sk" to "Ahoj, ako sa máš dnes?",
            "sl" to "Živjo, kako si danes?",
            "sq" to "Përshëndetje, si jeni sot?",
            "sv" to "Hej, hur mår du idag?",
            "sw" to "Habari, hujambo leo?",
            "ta" to "வணக்கம், இன்று எப்படி இருக்கிறீர்கள்?",
            "te" to "హలో, ఈరోజు మీరు ఎలా ఉన్నారు?",
            "th" to "สวัสดี วันนี้คุณเป็นอย่างไรบ้าง",
            "tl" to "Kumusta, kamusta ka ngayon?",
            "tr" to "Merhaba, bugün nasılsın?",
            "uk" to "Привіт, як ти сьогодні?",
            "ur" to "ہیلو، آج آپ کیسے ہیں؟",
            "vi" to "Xin chào, hôm nay bạn khỏe không?",
            "zh" to "你好，你今天怎么样？"
        )
    }
}
