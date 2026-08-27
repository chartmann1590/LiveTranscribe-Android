package com.charles.livecaptionn.review

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object PlayReviewHelper {
    suspend fun requestAndLaunch(activity: Activity) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            val reviewInfo = manager.requestReviewFlow().await()
            manager.launchReviewFlow(activity, reviewInfo).await()
        } catch (_: Exception) {
        }
    }

    fun launchIfEligible(activity: Activity, launchCount: Int, minLaunches: Int = 3) {
        if (launchCount < minLaunches) return
        if ((launchCount - minLaunches) % 5 != 0) return
        CoroutineScope(Dispatchers.Main).launch {
            requestAndLaunch(activity)
        }
    }
}
