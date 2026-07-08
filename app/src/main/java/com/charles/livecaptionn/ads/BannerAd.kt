package com.charles.livecaptionn.ads

import android.util.Log
import android.widget.LinearLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.charles.livecaptionn.LiveCaptionApp
import com.charles.livecaptionn.billing.PremiumState
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    if (!AdUnits.ENABLED || AdUnits.BANNER.isBlank()) return

    val app = LocalContext.current.applicationContext as LiveCaptionApp
    val premium by app.container.premiumRepository.state.collectAsState(initial = PremiumState.EMPTY)
    if (premium.hasAdFree) return

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdUnits.BANNER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        Log.d("BannerAd", "Banner ad loaded.")
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w("BannerAd", "Banner ad failed to load: ${error.message}")
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
