package com.charles.livecaptionn

import android.app.Application
import com.charles.livecaptionn.di.AppContainer
import com.charles.livecaptionn.update.UpdateCheckWorker

class LiveCaptionApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Kick off the periodic update check. Uses KEEP policy so re-enqueueing
        // on every cold start is cheap and idempotent.
        UpdateCheckWorker.schedule(this)
    }
}
