package com.charles.livecaptionn.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.charles.livecaptionn.billing.PremiumRepository
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Loads and serves the AdMob "app-open" ad format — the full-screen ad
 * that flashes briefly when a user brings the app to the foreground.
 *
 * Lifecycle wiring:
 *  - `Application.registerActivityLifecycleCallbacks` tracks the current
 *    foreground activity so we always have a host to show the ad on.
 *  - `ProcessLifecycleOwner.lifecycle` fires `ON_START` once per cold or
 *    warm app resume. That is the canonical "app came to foreground"
 *    signal and is what Google's own sample code hooks.
 *
 * Ads expire four hours after load per Google's SDK contract, so we cache
 * both the ad and its load timestamp and refetch when stale.
 */
class AppOpenAdManager(
    private val application: Application,
    private val premiumRepository: PremiumRepository,
    private val appScope: CoroutineScope
) : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var loadTime: Long = 0

    private var currentActivity: Activity? = null
    /** Only show the app-open ad once per process lifetime (cold start),
     *  not on every warm resume. Repeated ads on every foreground are
     *  intrusive and contradict the app's utility-focused design. */
    private var hasShownAd = false

    /** Kept in sync from [premiumRepository]'s Flow; checked right before showing an ad
     *  since entitlement can change mid-session (e.g. right after a Custom Tab purchase). */
    @Volatile private var hasAdFree = false

    fun attach() {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        appScope.launch {
            premiumRepository.state.collect { hasAdFree = it.hasAdFree }
        }
        // Preload immediately so the first foreground opportunity can show fast.
        loadAd()
    }

    // ── Foreground signal ──

    override fun onStart(owner: LifecycleOwner) {
        // Only show on first start (cold start), not warm resumes.
        if (hasShownAd) return
        hasShownAd = true
        if (hasAdFree) return
        val activity = currentActivity ?: return
        showAdIfAvailable(activity)
    }

    // ── Activity tracking ──

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        // Don't overwrite while we're mid-show; the ad itself is a separate
        // activity on top of the app's real activity.
        if (!isShowingAd) currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    // ── Load / show ──

    private fun loadAd() {
        if (!AdUnits.ENABLED || AdUnits.APP_OPEN.isBlank()) return
        if (isLoadingAd || isAdAvailable()) return
        isLoadingAd = true
        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            application,
            AdUnits.APP_OPEN,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time
                    Log.d(TAG, "App-open ad loaded.")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAd = false
                    Log.w(TAG, "App-open ad failed to load: ${error.message}")
                }
            }
        )
    }

    private fun showAdIfAvailable(activity: Activity) {
        if (isShowingAd) return
        if (!isAdAvailable()) {
            loadAd()
            return
        }
        val ad = appOpenAd ?: return
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                isShowingAd = false
                Log.w(TAG, "App-open ad failed to show: ${adError.message}")
                loadAd()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App-open ad shown.")
            }
        }
        isShowingAd = true
        ad.show(activity)
    }

    private fun isAdAvailable(): Boolean {
        val fresh = Date().time - loadTime < AD_TTL_MS
        return appOpenAd != null && fresh
    }

    companion object {
        private const val TAG = "AppOpenAdManager"
        // Google's SDK contract: app-open ads expire four hours after load.
        private const val AD_TTL_MS = 4 * 60 * 60 * 1000L
    }
}
