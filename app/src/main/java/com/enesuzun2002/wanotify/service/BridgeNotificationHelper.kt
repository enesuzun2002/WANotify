package com.enesuzun2002.wanotify.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.enesuzun2002.wanotify.R
import com.enesuzun2002.wanotify.core.constants.AppConstants

class BridgeNotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createSilentChannel()
    }

    private fun createSilentChannel() {
        val channel = NotificationChannel(
            AppConstants.CHANNEL_ID,
            AppConstants.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Silent bridge channel for smartwatch sync"
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun dispatchSilentNotification(id: Int, title: String, text: String) {
        val builder = NotificationCompat.Builder(context, AppConstants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setSilent(true)
            .setAutoCancel(true)

        notificationManager.notify(id, builder.build())
    }
}