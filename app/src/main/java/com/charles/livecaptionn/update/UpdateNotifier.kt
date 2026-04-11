package com.charles.livecaptionn.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * Posts a single heads-up notification when a new release is detected, with a
 * direct "Download" action that opens the APK URL (falling back to the release
 * page) in the user's browser. Remembers the last tag it notified about so
 * the same release doesn't ping the user on every periodic check.
 */
class UpdateNotifier(private val context: Context) {

    private val prefs get() = context.updateDataStore

    suspend fun notifyIfNew(info: UpdateInfo) {
        val lastShown = prefs.data.first()[LAST_TAG_KEY]
        if (lastShown == info.tagName) return

        ensureChannel()
        if (!canPostNotifications()) return

        val openUrl = info.apkDownloadUrl ?: info.releasePageUrl
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(openUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openPending = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            browserIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("LiveCaptionN update available")
            .setContentText("${info.releaseName} is ready to install")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        append("${info.releaseName} is ready to install.\n\n")
                        if (info.notes.isNotBlank()) {
                            append(info.notes.take(400))
                        }
                    }
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(
                android.R.drawable.stat_sys_download,
                "Download",
                openPending
            )
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
        prefs.edit { it[LAST_TAG_KEY] = info.tagName }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies you when a new version of LiveCaptionN is available on GitHub"
        }
        nm.createNotificationChannel(channel)
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val CHANNEL_ID = "app_updates"
        private const val NOTIF_ID = 5001
        private const val REQUEST_OPEN = 10_001
        private val LAST_TAG_KEY = stringPreferencesKey("last_notified_tag")

        private val Context.updateDataStore by preferencesDataStore(name = "update_notifier")
    }
}
