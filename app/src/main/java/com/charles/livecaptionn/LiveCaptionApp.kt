package com.charles.livecaptionn

import android.app.Application
import com.charles.livecaptionn.ads.AppOpenAdManager
import com.charles.livecaptionn.ads.AdUnits
import com.charles.livecaptionn.ads.AdsInitState
import com.charles.livecaptionn.di.AppContainer
import com.charles.livecaptionn.update.UpdateCheckWorker
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.ktx.performance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveCaptionApp : Application() {
    lateinit var container: AppContainer
        private set
    private var appOpenAdManager: AppOpenAdManager? = null

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // WorkManager.getInstance()/enqueue touch WorkManager's Room database; keep that
        // off the main-thread startup path.
        container.appScope.launch { UpdateCheckWorker.schedule(this@LiveCaptionApp) }

        FirebaseApp.initializeApp(this)
        // Don't pollute the Crashlytics dashboard or skew Analytics with developer
        // builds. Performance Monitoring follows the same toggle.
        val collectInProd = !BuildConfig.DEBUG
        Firebase.crashlytics.isCrashlyticsCollectionEnabled = collectInProd
        Firebase.analytics.setAnalyticsCollectionEnabled(collectInProd)
        Firebase.performance.isPerformanceCollectionEnabled = collectInProd

        if (!AdUnits.ENABLED) return

        // Google Mobile Ads' own SDK init (classloading + adapter setup) can be slow
        // enough on a cold start to trip the main-thread ANR watchdog if it runs
        // synchronously here. Google's docs support initializing off the main thread,
        // so push it (and the dependent AppOpenAdManager attach, which does need the
        // main thread for its lifecycle-callback registration) onto the app's scope
        // instead of blocking Application.onCreate().
        container.appScope.launch {
            if (BuildConfig.DEBUG) {
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder()
                        .setTestDeviceIds(listOf("ECE881749D58EF0DA0CED390014532FF"))
                        .build()
                )
            }
            // initialize() returns before the SDK is actually ready, and while it is still
            // initializing it holds internal locks. Any main-thread ad call made in that
            // window (banner/native AdView creation, AppOpenAd.load) blocks on those locks,
            // which is the "blamed thread waiting for too long" ANR whose blame frame is
            // this coroutine (Crashlytics #138/#152/#157/#158). So: publish readiness from
            // the completion callback, and let every ad surface wait for it.
            MobileAds.initialize(this@LiveCaptionApp) { AdsInitState.markReady() }
            AdsInitState.ready.first { it }
            withContext(Dispatchers.Main) {
                appOpenAdManager = AppOpenAdManager(
                    this@LiveCaptionApp,
                    container.premiumRepository,
                    container.appScope
                ).also { it.attach() }
            }
        }
    }
}
