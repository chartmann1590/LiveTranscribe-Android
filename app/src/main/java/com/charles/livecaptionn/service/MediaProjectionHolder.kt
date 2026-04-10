package com.charles.livecaptionn.service

import android.app.Activity
import android.content.Intent

/** Holds the MediaProjection consent result between Activity and Service. */
object MediaProjectionHolder {
    @Volatile var resultCode: Int = Activity.RESULT_CANCELED
    @Volatile var data: Intent? = null

    @Synchronized
    fun set(code: Int, intent: Intent) {
        resultCode = code
        data = intent
    }

    @Synchronized
    fun clear() {
        resultCode = Activity.RESULT_CANCELED
        data = null
    }
}
