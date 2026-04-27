package com.charles.livecaptionn.ads

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.charles.livecaptionn.R
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Material card that hosts a Google AdMob native ad. The card is collapsed
 * (renders nothing) until the ad finishes loading; this avoids reserving
 * vertical space for an ad that might fail to fill.
 *
 * Loads exactly once per Composable lifecycle (DisposableEffect with Unit
 * key). Destroys the ad on dispose so the SDK doesn't leak references to a
 * stale view tree.
 */
@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    if (!AdUnits.ENABLED || AdUnits.NATIVE.isBlank()) return

    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(Unit) {
        val loader = AdLoader.Builder(context, AdUnits.NATIVE)
            .forNativeAd { ad ->
                // If a previous ad was loaded but never displayed (race during
                // recomposition), free it before swapping in the new one.
                nativeAd?.destroy()
                nativeAd = ad
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Native ad failed: ${error.code} ${error.message}")
                }

                override fun onAdLoaded() {
                    Log.d(TAG, "Native ad loaded.")
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()

        loader.loadAd(AdRequest.Builder().build())

        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    val ad = nativeAd ?: return
    val ctaBg = MaterialTheme.colorScheme.primary.toArgb()
    val ctaFg = MaterialTheme.colorScheme.onPrimary.toArgb()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                val view = LayoutInflater.from(ctx)
                    .inflate(R.layout.native_ad_view, null) as NativeAdView
                bindNativeAd(ad, view, ctaBg, ctaFg)
                view
            },
            update = { view -> bindNativeAd(ad, view as NativeAdView, ctaBg, ctaFg) }
        )
    }
}

/**
 * Wires NativeAd assets into the inflated NativeAdView. Each asset's
 * dedicated view ID is found, populated, and registered on the
 * NativeAdView; setNativeAd() must be called last so AdMob can track
 * impressions / clicks.
 */
private fun bindNativeAd(
    ad: NativeAd,
    view: NativeAdView,
    ctaBg: Int,
    ctaFg: Int
) {
    val headline = view.findViewById<TextView>(R.id.ad_headline)
    headline.text = ad.headline
    view.headlineView = headline

    val body = view.findViewById<TextView>(R.id.ad_body)
    if (ad.body.isNullOrBlank()) {
        body.visibility = View.GONE
    } else {
        body.text = ad.body
        body.visibility = View.VISIBLE
    }
    view.bodyView = body

    val cta = view.findViewById<Button>(R.id.ad_call_to_action)
    if (ad.callToAction.isNullOrBlank()) {
        cta.visibility = View.GONE
    } else {
        cta.text = ad.callToAction
        cta.setBackgroundColor(ctaBg)
        cta.setTextColor(ctaFg)
        cta.visibility = View.VISIBLE
    }
    view.callToActionView = cta

    val icon = view.findViewById<ImageView>(R.id.ad_app_icon)
    val drawable = ad.icon?.drawable
    if (drawable == null) {
        icon.visibility = View.GONE
    } else {
        icon.setImageDrawable(drawable)
        icon.visibility = View.VISIBLE
    }
    view.iconView = icon

    view.setNativeAd(ad)
}

private const val TAG = "NativeAdCard"
