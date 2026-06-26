package com.charles.livecaptionn.speech

enum class RecognitionStatus {
    IDLE,
    LISTENING,
    PROCESSING,
    PAUSED,
    ERROR;

    val displayName: String
        get() = when (this) {
            IDLE -> "Idle"
            LISTENING -> "Listening"
            PROCESSING -> "Processing"
            PAUSED -> "Paused"
            ERROR -> "Error"
        }
}
