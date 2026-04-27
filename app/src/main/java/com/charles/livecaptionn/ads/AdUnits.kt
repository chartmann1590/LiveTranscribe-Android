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
    val ENABLED: Boolean = BuildConfig.ADS_ENABLED

    val APP_OPEN: String = if (BuildConfig.DEBUG)
        BuildConfig.ADMOB_APP_OPEN_ID_DEBUG
    else
        BuildConfig.ADMOB_APP_OPEN_ID_RELEASE

    val BANNER: String = if (BuildConfig.DEBUG)
        BuildConfig.ADMOB_BANNER_ID_DEBUG
    else
        BuildConfig.ADMOB_BANNER_ID_RELEASE

    val NATIVE: String = if (BuildConfig.DEBUG)
        BuildConfig.ADMOB_NATIVE_ID_DEBUG
    else
        BuildConfig.ADMOB_NATIVE_ID_RELEASE
}
