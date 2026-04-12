package com.charles.livecaptionn

import android.app.Application
import com.charles.livecaptionn.ads.AppOpenAdManager
import com.charles.livecaptionn.di.AppContainer
import com.charles.livecaptionn.update.UpdateCheckWorker
import com.google.android.gms.ads.MobileAds

class LiveCaptionApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        UpdateCheckWorker.schedule(this)

        MobileAds.initialize(this) {}
        AppOpenAdManager(this).attach()
    }
}
