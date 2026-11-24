package com.charles.livecaptionn

import com.charles.livecaptionn.settings.Language
import com.charles.livecaptionn.speech.VoskModelCatalog
import com.charles.livecaptionn.translation.MlKitLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tier-1 (fast, JVM-only) catalog integrity for the language-verification
 * automation. These checks catch catalog rot — a typo'd code, a language
 * that can be transcribed but not translated, a bundled model that is not
 * in the downloadable catalog — on every PR, without an emulator or any
 * network. Live download/translate proof lives in the androidTest sweep
 * (AllLanguagesVerificationTest / VoskAllLanguagesTest) and the workflow's
 * curl step.
 */
class LanguageCatalogIntegrityTest {

    @Test
    fun mlKitList_codesAreUniqueWellFormedAndComplete() {
        val codes = MlKitLanguages.LIST.map { it.code }
        assertEquals("ML Kit codes must be unique", codes.size, codes.toSet().size)
        for (lang in MlKitLanguages.LIST) {
            assertTrue(
                "Code '${lang.code}' must be a lowercase 2-letter tag",
                lang.code.matches(Regex("[a-z]{2}"))
            )
            assertTrue("Name for '${lang.code}' must not be blank", lang.name.isNotBlank())
        }
        // The list mirrors TranslateLanguage.getAllLanguages() (59 in ML Kit
        // 17.0.3). If Google adds/removes a language this count changes on
        // purpose — update MlKitLanguages and this number together.
        assertEquals(59, MlKitLanguages.LIST.size)
        assertTrue(codes.contains("en"))
    }

    @Test
    fun mlKitFreeTier_isSubsetOfList() {
        val codes = MlKitLanguages.LIST.map { it.code }.toSet()
        for (free in MlKitLanguages.FREE_TIER) {
            assertTrue("Free-tier code '$free' is not in the ML Kit list", free in codes)
        }
    }

    @Test
    fun fallbackLanguages_areAllTranslatable() {
        val codes = MlKitLanguages.LIST.map { it.code }.toSet()
        for (lang in Language.FALLBACK) {
            assertTrue(
                "Fallback language '${lang.code}' is not in the ML Kit list",
                lang.code in codes
            )
        }
    }

    @Test
    fun everyVoskLanguage_isAlsoTranslatable() {
        // A language the user can transcribe but never translate (or vice
        // versa in the picker) is a broken promise in the UI.
        val mlKitCodes = MlKitLanguages.LIST.map { it.code }.toSet()
        val voskCodes = VoskModelCatalog.DOWNLOADABLE.map { it.languageCode.lowercase() }.toSet()
        for (code in voskCodes) {
            assertTrue("Vosk language '$code' is not in the ML Kit list", code in mlKitCodes)
        }
        assertEquals(19, voskCodes.size)
    }

    @Test
    fun bundledModels_existInCatalogAndAssetsWhenStaged() {
        for (bundled in VoskModelCatalog.BUNDLED_MODELS) {
            val entry = VoskModelCatalog.findByModelName(bundled.modelName)
            assertTrue("Bundled model ${bundled.modelName} missing from catalog", entry != null)
            assertEquals(bundled.languageCode, entry!!.languageCode)
        }
        // assets/models is gitignored — release builds stage the model files
        // there before packaging. When the tree is present (local/release
        // builds), every bundled model must actually be in it; in CI checkouts
        // it is absent and this part is a no-op by design.
        val candidates = listOf(
            File("src/main/assets/models"),
            File("app/src/main/assets/models")
        )
        val assetsDir = candidates.firstOrNull { it.isDirectory } ?: return
        for (bundled in VoskModelCatalog.BUNDLED_MODELS) {
            assertTrue(
                "Bundled model ${bundled.modelName} not staged under $assetsDir",
                File(assetsDir, bundled.modelName).isDirectory
            )
        }
    }

    @Test
    fun voskSmallestVariantPerLanguage_isReasonable() {
        val byLanguage = VoskModelCatalog.DOWNLOADABLE.groupBy { it.languageCode.lowercase() }
        for ((code, entries) in byLanguage) {
            val smallest = entries.minByOrNull { it.sizeMb }!!
            assertTrue(
                "Smallest $code model is ${smallest.sizeMb} MB — suspiciously large for a 'smallest' variant",
                smallest.sizeMb <= 400
            )
            assertTrue(
                "Model ${smallest.modelName} has no download URL",
                !smallest.downloadUrl.isNullOrBlank()
            )
        }
    }
}
