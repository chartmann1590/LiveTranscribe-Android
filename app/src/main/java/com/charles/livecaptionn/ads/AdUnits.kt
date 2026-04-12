package com.charles.livecaptionn.ads

import com.charles.livecaptionn.BuildConfig

/**
 * AdMob unit IDs for LiveCaptionN.
 *
 * Debug builds use Google's official test ad units (which always fill) so
 * you can verify the layout without waiting for real ad inventory to ramp
 * up. Release builds use the real production ad units.
 */
object AdUnits {
    val APP_OPEN: String = if (BuildConfig.DEBUG)
        "ca-app-pub-3940256099942544/9257395921"
    else
        "ca-app-pub-8382831211800454/6447868787"

    val BANNER: String = if (BuildConfig.DEBUG)
        "ca-app-pub-3940256099942544/9214589741"
    else
        "ca-app-pub-8382831211800454/2231636617"
}
