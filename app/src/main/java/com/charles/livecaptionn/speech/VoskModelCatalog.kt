package com.charles.livecaptionn.speech

/**
 * Where the Vosk model files live.
 *
 * - [BUNDLED]: shipped inside the APK under `assets/models/<name>`.
 * - [DOWNLOADED]: unzipped into the app's private `filesDir/vosk/<name>` folder.
 */
enum class VoskSource { BUNDLED, DOWNLOADED }

/**
 * Size/accuracy tier of a Vosk model. SMALL models are fast and memory-light
 * (30–80 MB) and are the right default for mobile. LARGE models are more
 * accurate but can be hundreds of MB to a few GB — only recommended when the
 * user has the space and wants maximum transcription quality for a single
 * language.
 */
enum class ModelQuality { SMALL, LARGE }

/**
 * Metadata about one Vosk speech model. `installed = true` means the files
 * are present and ready to load; `downloadUrl` is populated for models the
 * user can fetch at runtime.
 */
data class VoskModelInfo(
    val languageCode: String,
    val languageName: String,
    val modelName: String,
    val sizeMb: Int,
    val downloadUrl: String? = null,
    val installed: Boolean = false,
    val source: VoskSource = VoskSource.DOWNLOADED,
    val isBundled: Boolean = false,
    val quality: ModelQuality = ModelQuality.SMALL
)

/**
 * Static catalog of small Vosk models we know how to fetch. Sourced from
 * https://alphacephei.com/vosk/models — keep the list intentionally short
 * and weighted toward the "-small" variants so downloads stay reasonable
 * on mobile data.
 */
object VoskModelCatalog {

    val DOWNLOADABLE: List<VoskModelInfo> = listOf(
        // ── Small / fast (default tier) ─────────────────────────────────────
        small("en", "English",       "vosk-model-small-en-us-0.15",  40),
        small("vi", "Vietnamese",    "vosk-model-small-vn-0.4",      32),
        small("es", "Spanish",       "vosk-model-small-es-0.42",     39),
        small("fr", "French",        "vosk-model-small-fr-0.22",     41),
        small("de", "German",        "vosk-model-small-de-0.15",     45),
        small("it", "Italian",       "vosk-model-small-it-0.22",     48),
        small("pt", "Portuguese",    "vosk-model-small-pt-0.3",      31),
        small("nl", "Dutch",         "vosk-model-small-nl-0.22",     39),
        small("ru", "Russian",       "vosk-model-small-ru-0.22",     45),
        small("uk", "Ukrainian",     "vosk-model-small-uk-v3-small", 75),
        small("pl", "Polish",        "vosk-model-small-pl-0.22",     50),
        small("cs", "Czech",         "vosk-model-small-cs-0.4-rhasspy", 44),
        small("tr", "Turkish",       "vosk-model-small-tr-0.3",      35),
        small("ar", "Arabic",        "vosk-model-ar-mgb2-0.4",       318), // no small variant
        small("fa", "Persian",       "vosk-model-small-fa-0.42",     47),
        small("hi", "Hindi",         "vosk-model-small-hi-0.22",     42),
        small("zh", "Chinese",       "vosk-model-small-cn-0.22",     42),
        small("ja", "Japanese",      "vosk-model-small-ja-0.22",     48),
        small("ko", "Korean",        "vosk-model-small-ko-0.22",     82),
        small("id", "Indonesian",    "vosk-model-small-id-0.22",     30),

        // ── Large / accurate (optional, for users with space to burn) ─────
        // Small variants stay listed above so users can pick the tier that
        // matches their phone. Large models run slower and use more RAM but
        // give meaningfully better WER on the same audio.
        large("vi", "Vietnamese",    "vosk-model-vn-0.4",            78),   // server-grade, still mobile-friendly
        large("uk", "Ukrainian",     "vosk-model-uk-v3",             343),
        large("en", "English",       "vosk-model-en-us-0.22",        1800), // 5.69 WER
        large("es", "Spanish",       "vosk-model-es-0.42",           1400),
        large("fr", "French",        "vosk-model-fr-0.22",           1400),
        large("de", "German",        "vosk-model-de-0.21",           1900),
        large("it", "Italian",       "vosk-model-it-0.22",           1200),
        large("pt", "Portuguese",    "vosk-model-pt-fb-v0.1.1-20220516_2113", 1600),
        large("nl", "Dutch",         "vosk-model-nl-spraakherkenning-0.6",    860),
        large("ru", "Russian",       "vosk-model-ru-0.42",           1800),
        large("fa", "Persian",       "vosk-model-fa-0.42",           1600),
        large("hi", "Hindi",         "vosk-model-hi-0.22",           1500),
        large("zh", "Chinese",       "vosk-model-cn-0.22",           1300),
        large("ja", "Japanese",      "vosk-model-ja-0.22",           1000),
        large("ar", "Arabic",        "vosk-model-ar-0.22-linto-1.1.0", 1300),
    )

    /** The lightweight variants that ship inside the APK. */
    val BUNDLED_MODELS: List<BundledModel> = listOf(
        BundledModel(languageCode = "en", modelName = "vosk-model-small-en-us-0.15"),
        BundledModel(languageCode = "vi", modelName = "vosk-model-small-vn-0.4")
    )

    data class BundledModel(val languageCode: String, val modelName: String)

    fun findByLanguage(code: String): VoskModelInfo? =
        DOWNLOADABLE.firstOrNull { it.languageCode.equals(code, ignoreCase = true) }

    fun findByModelName(modelName: String): VoskModelInfo? =
        DOWNLOADABLE.firstOrNull { it.modelName == modelName }

    private fun small(
        code: String,
        name: String,
        modelName: String,
        sizeMb: Int
    ) = VoskModelInfo(
        languageCode = code,
        languageName = name,
        modelName = modelName,
        sizeMb = sizeMb,
        downloadUrl = "https://alphacephei.com/vosk/models/$modelName.zip",
        quality = ModelQuality.SMALL
    )

    private fun large(
        code: String,
        name: String,
        modelName: String,
        sizeMb: Int
    ) = VoskModelInfo(
        languageCode = code,
        languageName = name,
        modelName = modelName,
        sizeMb = sizeMb,
        downloadUrl = "https://alphacephei.com/vosk/models/$modelName.zip",
        quality = ModelQuality.LARGE
    )
}
