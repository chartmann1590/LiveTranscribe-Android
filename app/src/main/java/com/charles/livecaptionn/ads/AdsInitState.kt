package com.charles.livecaptionn.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide "Mobile Ads SDK finished initializing" flag.
 *
 * MobileAds.initialize() runs off the main thread (see LiveCaptionApp), but until its
 * completion callback fires the SDK holds internal locks. Creating an AdView / AdLoader
 * or calling loadAd() on the main thread during that window blocks the UI thread and
 * shows up as an ANR. Ad surfaces observe [ready] and only touch the SDK once it is true.
 */
object AdsInitState {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    fun markReady() {
        _ready.value = true
    }
}
