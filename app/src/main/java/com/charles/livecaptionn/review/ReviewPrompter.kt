package com.charles.livecaptionn.review

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Triggers Google Play's in-app review flow after a user has completed a
 * few captioning sessions — a signal they got value out of the app rather
 * than bouncing immediately. Only ever asks once per install; Play's own
 * quota limits repeat prompts anyway even if we asked again.
 */
object ReviewPrompter {

    private const val PREFS_NAME = "review_prompter"
    private const val KEY_SESSIONS_COMPLETED = "sessions_completed"
    private const val KEY_ALREADY_PROMPTED = "already_prompted"
    private const val SESSIONS_BEFORE_PROMPT = 3

    fun onCaptioningSessionCompleted(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ALREADY_PROMPTED, false)) return

        val completed = prefs.getInt(KEY_SESSIONS_COMPLETED, 0) + 1
        prefs.edit().putInt(KEY_SESSIONS_COMPLETED, completed).apply()
        if (completed < SESSIONS_BEFORE_PROMPT) return

        prefs.edit().putBoolean(KEY_ALREADY_PROMPTED, true).apply()

        val manager = ReviewManagerFactory.create(activity)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            manager.launchReviewFlow(activity, task.result)
        }
    }
}
