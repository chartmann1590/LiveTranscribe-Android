package com.charles.livecaptionn.settings

enum class SttBackend(val label: String) {
    REMOTE_WHISPER("Remote Whisper"),
    LOCAL_VOSK("Local Vosk");

    companion object {
        fun fromName(name: String): SttBackend =
            entries.firstOrNull { it.name == name } ?: REMOTE_WHISPER
    }
}
