package com.enesuzun2002.wanotify.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.enesuzun2002.wanotify.core.constants.AppConstants
import com.enesuzun2002.wanotify.domain.filter.NotificationDeduplicator
import java.util.concurrent.ConcurrentHashMap

class WatchNotificationListener : NotificationListenerService() {

    private val tag = "WatchNotificationListener"
    private val deduplicator = NotificationDeduplicator()
    private lateinit var bridgeHelper: BridgeNotificationHelper

    // Maps: Conversation Key (sbn.tag ?: sbn.id.toString()) -> Set of dispatched bridge notification IDs
    private val activeBridgeIdsPerChat = ConcurrentHashMap<String, MutableSet<Int>>()

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
        activeBridgeIdsPerChat.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != AppConstants.WHATSAPP_PACKAGE) return

        try {
            val clean = deduplicator.evaluate(sbn) ?: return
            val conversationKey = sbn.tag ?: sbn.id.toString()

            // Track the ID dispatched for this conversation
            activeBridgeIdsPerChat.computeIfAbsent(conversationKey) {
                ConcurrentHashMap.newKeySet()
            }.add(clean.stableId)

            bridgeHelper.dispatchSilentNotification(
                id = clean.stableId,
                title = clean.title,
                text = clean.text
            )
        } catch (e: Throwable) {
            Log.e(tag, "Error evaluating notification event: ${e.message}", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != AppConstants.WHATSAPP_PACKAGE) return

        try {
            val remainingWhatsAppNotifications = activeNotifications?.any {
                it.packageName == AppConstants.WHATSAPP_PACKAGE
            } ?: false

            if (!remainingWhatsAppNotifications) {
                // No WhatsApp notifications left on the device at all
                bridgeHelper.cancelAllNotifications()
                activeBridgeIdsPerChat.clear()
                return
            }

            // Otherwise, handle the single removed chat
            val conversationKey = sbn.tag ?: sbn.id.toString()
            activeBridgeIdsPerChat.remove(conversationKey)?.forEach { id ->
                bridgeHelper.cancelNotification(id)
            }
        } catch (e: Throwable) {
            Log.e(tag, "Error evaluating notification event: ${e.message}", e)
        }
    }
}