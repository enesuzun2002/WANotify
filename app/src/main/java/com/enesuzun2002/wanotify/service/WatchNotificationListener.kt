package com.enesuzun2002.wanotify.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.enesuzun2002.wanotify.core.constants.AppConstants
import com.enesuzun2002.wanotify.domain.filter.NotificationDeduplicator

class WatchNotificationListener : NotificationListenerService() {

    private val tag = "WatchNotificationListener"
    private val deduplicator = NotificationDeduplicator()
    private lateinit var bridgeHelper: BridgeNotificationHelper

    override fun onCreate() {
        super.onCreate()
        bridgeHelper = BridgeNotificationHelper(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(tag, "Notification listener connected successfully.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(tag, "Notification listener disconnected.")
        deduplicator.reset()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != AppConstants.WHATSAPP_PACKAGE) return

        try {
            val clean = deduplicator.evaluate(sbn) ?: return

            bridgeHelper.dispatchSilentNotification(
                id = clean.stableId,
                title = clean.title,
                text = clean.text
            )
        } catch (e: Throwable) {
            Log.e(tag, "Error evaluating notification event: ${e.message}", e)
        }
    }
}