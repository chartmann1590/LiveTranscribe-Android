package com.charles.livecaptionn

import com.charles.livecaptionn.speech.ModelQuality
import com.charles.livecaptionn.speech.VoskModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoskModelCatalogTest {

    @Test
    fun findByLanguage_knownCode_returnsModel() {
        val model = VoskModelCatalog.findByLanguage("en")
        assertNotNull(model)
        assertEquals("en", model?.languageCode)
    }

    @Test
    fun findByLanguage_unknownCode_returnsNull() {
        assertNull(VoskModelCatalog.findByLanguage("xx"))
    }

    @Test
    fun findByLanguage_caseInsensitive() {
        val model = VoskModelCatalog.findByLanguage("EN")
        assertNotNull(model)
    }

    @Test
    fun findByModelName_knownName_returnsModel() {
        val model = VoskModelCatalog.findByModelName("vosk-model-small-en-us-0.15")
        assertNotNull(model)
        assertEquals("en", model?.languageCode)
    }

    @Test
    fun findByModelName_unknownName_returnsNull() {
        assertNull(VoskModelCatalog.findByModelName("nonexistent-model"))
    }

    @Test
    fun bundledModels_appearInDownloadable() {
        for (bundled in VoskModelCatalog.BUNDLED_MODELS) {
            val found = VoskModelCatalog.findByModelName(bundled.modelName)
            assertNotNull("Bundled model ${bundled.modelName} not in DOWNLOADABLE", found)
        }
    }

    @Test
    fun downloadable_allHaveUrls() {
        for (model in VoskModelCatalog.DOWNLOADABLE) {
            assertNotNull("Model ${model.modelName} has no downloadUrl", model.downloadUrl)
            assertTrue(
                "Model ${model.modelName} URL doesn't end with .zip",
                model.downloadUrl!!.endsWith(".zip")
            )
        }
    }

    @Test
    fun largeModels_haveLargeQuality() {
        for (model in VoskModelCatalog.DOWNLOADABLE) {
            if (model.sizeMb > 300) {
                assertEquals(
                    "Large model ${model.modelName} (${model.sizeMb}MB) should be LARGE quality",
                    ModelQuality.LARGE,
                    model.quality
                )
            }
        }
    }

    @Test
    fun arabicModel_isLarge() {
        val model = VoskModelCatalog.findByLanguage("ar")
        assertNotNull(model)
        assertEquals(ModelQuality.LARGE, model?.quality)
    }
}
