package com.enesuzun2002.wanotify.domain.filter

import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.enesuzun2002.wanotify.core.constants.AppConstants
import com.enesuzun2002.wanotify.domain.model.ProcessedNotification
import java.util.concurrent.ConcurrentHashMap

class NotificationDeduplicator {

    // Maps: Chat ID -> Timestamp of the latest processed message leaf
    private val lastDeliveredTimestamps = ConcurrentHashMap<Int, Long>()

    // Message signature cache: Guards against identical messages re-emitted over long periods (e.g. quoted replies)
    // Key: "chatId:sender:text" -> Value: processed timestamp
    private val processedMessageSignatures = ConcurrentHashMap<String, Long>()

    /**
     * Clears all cached timestamps and message signatures.
     * Useful for debugging, testing, or when restarting listener services.
     */
    fun reset() {
        lastDeliveredTimestamps.clear()
        processedMessageSignatures.clear()
    }

    fun evaluate(sbn: StatusBarNotification): ProcessedNotification? {
        if (sbn.packageName != AppConstants.WHATSAPP_PACKAGE) return null

        val notification = sbn.notification

        // 1. Drop persistent/ongoing and Android group summary notifications
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return null
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null

        val extras = notification.extras ?: return null

        // 2. Drop structural sub-summaries
        if (extras.containsKey(Notification.EXTRA_SUMMARY_TEXT)) return null

        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        if (template.isNotEmpty() && !template.contains("MessagingStyle")) return null

        // 3. Extract MessagingStyle
        val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            ?: return null

        if (messagingStyle.messages.isEmpty()) return null

        // Extract strictly the most recent message leaf node
        val latestMessage = messagingStyle.messages.last()
        val text = latestMessage.text?.toString().orEmpty().trim()
        val messageTime = latestMessage.timestamp

        // Drop system counter summaries masquerading without Person
        if (latestMessage.person == null && !messagingStyle.isGroupConversation) {
            return null
        }

        if (text.isBlank()) return null

        val senderName = latestMessage.person?.name?.toString().orEmpty()
        val convTitle = messagingStyle.conversationTitle?.toString().orEmpty()

        val rawTitle = when {
            messagingStyle.isGroupConversation && convTitle.isNotBlank() && senderName.isNotBlank() ->
                "$convTitle ($senderName)"
            messagingStyle.isGroupConversation && convTitle.isNotBlank() ->
                convTitle
            senderName.isNotBlank() ->
                senderName
            else ->
                extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        }.trim()

        if (rawTitle.isBlank()) return null

        val chatId = sbn.id
        val now = System.currentTimeMillis()

        // 4. Clean up signatures older than 1 HOUR
        processedMessageSignatures.entries.removeIf { now - it.value > 3_600_000L }

        // 5. Stable Signature: Exclude the dynamic chat title counter (e.g. "(2 mesaj)")
        // Signature relies ONLY on: chatId + sender + text
        val messageSignature = "$chatId:$senderName:$text"

        if (processedMessageSignatures.containsKey(messageSignature)) {
            return null
        }

        val previousTimestamp = lastDeliveredTimestamps[chatId] ?: 0L
        if (messageTime < previousTimestamp) {
            return null
        }

        // Update tracking states
        processedMessageSignatures[messageSignature] = now
        lastDeliveredTimestamps[chatId] = messageTime

        return ProcessedNotification(
            title = "[WA] $rawTitle",
            text = text,
            timestamp = messageTime
        )
    }
}