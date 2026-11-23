package com.charles.livecaptionn

import com.charles.livecaptionn.speech.ModelQuality
import com.charles.livecaptionn.speech.VoskModelCatalog
import com.charles.livecaptionn.speech.VoskModelRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

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
    fun catalog_hasNoDuplicateModelNames() {
        val names = VoskModelCatalog.DOWNLOADABLE.map { it.modelName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun downloadable_urlsPointAtTheirOwnModelZip() {
        // A catalog row whose URL names a different (or non-existent) archive
        // is a "Get" button that can never work.
        for (model in VoskModelCatalog.DOWNLOADABLE) {
            assertEquals(
                "https://alphacephei.com/vosk/models/${model.modelName}.zip",
                model.downloadUrl
            )
        }
    }

    @Test
    fun indonesian_isNotListedAsADownloadableModel() {
        // alphacephei.com publishes no Vosk model for Indonesian; the old
        // vosk-model-small-id-0.22 entry answered HTTP 404 (Play review, Oct 2026).
        assertNull(VoskModelCatalog.findByLanguage("id"))
    }

    @Test
    fun arabicModel_isLarge() {
        val model = VoskModelCatalog.findByLanguage("ar")
        assertNotNull(model)
        assertEquals(ModelQuality.LARGE, model?.quality)
    }

    @Test
    fun isValidModelDir_missingOrEmptyOrMarkerOnly_returnsFalse() {
        val root = Files.createTempDirectory("vosk-invalid-test").toFile()
        try {
            val missing = File(root, "missing")
            assertFalse(VoskModelRegistry.isValidModelDir(missing))

            val empty = File(root, "empty").apply { mkdirs() }
            assertFalse(VoskModelRegistry.isValidModelDir(empty))

            val markerOnly = File(root, "marker-only").apply {
                mkdirs()
                File(this, ".installed").writeText("ok")
            }
            assertFalse(VoskModelRegistry.isValidModelDir(markerOnly))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun isValidModelDir_confOrAmOrFinalMdl_returnsTrue() {
        val root = Files.createTempDirectory("vosk-valid-test").toFile()
        try {
            val withConf = File(root, "with-conf").apply { File(this, "conf").mkdirs() }
            assertTrue(VoskModelRegistry.isValidModelDir(withConf))

            val withAm = File(root, "with-am").apply { File(this, "am").mkdirs() }
            assertTrue(VoskModelRegistry.isValidModelDir(withAm))

            val withFinalMdl = File(root, "with-final").apply {
                mkdirs()
                File(this, "final.mdl").writeText("weights")
            }
            assertTrue(VoskModelRegistry.isValidModelDir(withFinalMdl))
        } finally {
            root.deleteRecursively()
        }
    }
}
