package com.charles.livecaptionn

import android.app.Application
import com.charles.livecaptionn.di.AppContainer

class LiveCaptionApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
