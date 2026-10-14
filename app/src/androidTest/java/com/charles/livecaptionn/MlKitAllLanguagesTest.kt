package com.charles.livecaptionn

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charles.livecaptionn.translation.MlKitLanguages
import com.charles.livecaptionn.translation.MlKitTranslationRepository
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Downloads every ML Kit translation model the app offers and translates a
 * sentence with each one, so a language the picker shows can never be silently
 * broken. Every pair the app offers goes through English models (ML Kit pivots
 * X -> en -> Y), so checking en -> X for every X covers each model a pair needs.
 *
 * Needs a device with network; downloads roughly 30 MB per language.
 * `./gradlew connectedPlaystoreDebugAndroidTest
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.charles.livecaptionn.MlKitAllLanguagesTest`
 */
@RunWith(AndroidJUnit4::class)
class MlKitAllLanguagesTest {

    @Test
    fun everyOfferedLanguage_downloadsAndTranslates() = runBlocking {
        val sentence = "Hello, this is a test of live captions."
        val repo = MlKitTranslationRepository()
        val failures = mutableListOf<String>()
        val offered = MlKitLanguages.LIST.map { it.code }.filter { it != "en" }

        // The hardcoded list must match what the SDK actually supports.
        val sdk = TranslateLanguage.getAllLanguages().toSet()
        offered.filter { TranslateLanguage.fromLanguageTag(it) !in sdk }
            .forEach { failures += "$it: not supported by the ML Kit SDK" }

        for (code in offered) {
            val started = System.currentTimeMillis()
            val out = withTimeoutOrNull(180_000) {
                runCatching {
                    repo.requireModel("en", code)
                    repo.translate(sentence, "en", code, autoDetect = false)
                }
            }
            val ms = System.currentTimeMillis() - started
            val text = out?.getOrNull().orEmpty()
            val ok = text.isNotBlank() && !text.equals(sentence, ignoreCase = true)
            val why = when {
                out == null -> "timed out"
                out.isFailure -> out.exceptionOrNull()?.toString() ?: "failed"
                !ok -> "no translation returned"
                else -> ""
            }
            Log.i(TAG, "MLKIT_PAIR en->$code ok=$ok ms=$ms pro=${MlKitLanguages.requiresPro(code)} out=$text $why")
            if (!ok) failures += "$code: $why"
        }
        Log.i(TAG, "MLKIT_DONE tested=${offered.size} failed=${failures.size} $failures")
        assertTrue("ML Kit languages that failed: $failures", failures.isEmpty())
    }

    private companion object {
        const val TAG = "MlKitAllLanguages"
    }
}
