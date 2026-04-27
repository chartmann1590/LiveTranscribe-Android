package com.charles.livecaptionn

import android.app.Application
import com.charles.livecaptionn.ads.AppOpenAdManager
import com.charles.livecaptionn.ads.AdUnits
import com.charles.livecaptionn.di.AppContainer
import com.charles.livecaptionn.update.UpdateCheckWorker
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.ktx.performance
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings

class LiveCaptionApp : Application() {
    lateinit var container: AppContainer
        private set
    private var appOpenAdManager: AppOpenAdManager? = null

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        UpdateCheckWorker.schedule(this)

        FirebaseApp.initializeApp(this)
        // Don't pollute the Crashlytics dashboard or skew Analytics with developer
        // builds. Performance Monitoring follows the same toggle.
        val collectInProd = !BuildConfig.DEBUG
        Firebase.crashlytics.isCrashlyticsCollectionEnabled = collectInProd
        Firebase.analytics.setAnalyticsCollectionEnabled(collectInProd)
        Firebase.performance.isPerformanceCollectionEnabled = collectInProd

        Firebase.remoteConfig.apply {
            setConfigSettingsAsync(remoteConfigSettings {
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600
            })
            fetchAndActivate()
        }

        if (!AdUnits.ENABLED) return

        if (BuildConfig.DEBUG) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf("ECE881749D58EF0DA0CED390014532FF"))
                    .build()
            )
        }
        MobileAds.initialize(this) {}
        appOpenAdManager = AppOpenAdManager(this).also { it.attach() }
    }
}
