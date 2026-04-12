package com.charles.livecaptionn

import android.app.Application
import com.charles.livecaptionn.ads.AppOpenAdManager
import com.charles.livecaptionn.di.AppContainer
import com.charles.livecaptionn.update.UpdateCheckWorker
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

class LiveCaptionApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        UpdateCheckWorker.schedule(this)

        if (BuildConfig.DEBUG) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf("ECE881749D58EF0DA0CED390014532FF"))
                    .build()
            )
        }
        MobileAds.initialize(this) {}
        AppOpenAdManager(this).attach()
    }
}
