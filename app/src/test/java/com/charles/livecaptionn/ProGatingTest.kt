package com.charles.livecaptionn

import com.charles.livecaptionn.speech.ModelQuality
import com.charles.livecaptionn.speech.VoskModelCatalog
import com.charles.livecaptionn.translation.MlKitLanguages
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Pro gating rules per language, pinned so they cannot drift by accident. */
class ProGatingTest {

    @Test
    fun indonesianTranslation_requiresPro() {
        assertTrue(MlKitLanguages.requiresPro("id"))
        assertTrue(MlKitLanguages.requiresPro("ID"))
    }

    @Test
    fun freeTierTranslationLanguages_doNotRequirePro() {
        for (code in MlKitLanguages.FREE_TIER) {
            assertFalse("$code is in the free tier", MlKitLanguages.requiresPro(code))
        }
    }

    @Test
    fun freeTier_isSubsetOfMlKitList() {
        val all = MlKitLanguages.LIST.map { it.code.lowercase() }.toSet()
        for (code in MlKitLanguages.FREE_TIER) {
            assertTrue("$code must be an ML Kit language", code.lowercase() in all)
        }
    }

    @Test
    fun everyProTranslationLanguage_isAnMlKitLanguage() {
        for (lang in MlKitLanguages.LIST) {
            // Either free or Pro; nothing in the picker can be neither.
            val free = lang.code.lowercase() in MlKitLanguages.FREE_TIER
            assertTrue("${lang.code}", free == !MlKitLanguages.requiresPro(lang.code))
        }
    }

    @Test
    fun everyLanguageWithALargeModel_alsoHasAFreeSmallModel_exceptArabic() {
        // Large (Pro) models are an upgrade, never the only option for a language.
        val large = VoskModelCatalog.DOWNLOADABLE.filter { it.quality == ModelQuality.LARGE }
        val small = VoskModelCatalog.DOWNLOADABLE.filter { it.quality == ModelQuality.SMALL }.map { it.languageCode }.toSet()
        for (model in large) {
            if (model.languageCode == "ar") continue // Vosk publishes no small Arabic model
            assertTrue("${model.languageCode} has only a Pro model", model.languageCode in small)
        }
    }
}
