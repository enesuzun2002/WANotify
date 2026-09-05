package com.enesuzun2002.wanotify.domain.filter

import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.enesuzun2002.wanotify.core.constants.AppConstants
import com.enesuzun2002.wanotify.domain.model.ProcessedNotification
import java.util.concurrent.ConcurrentHashMap

class NotificationDeduplicator(
    private val deduplicationWindowMs: Long = 10_000L
) {

    // Maps: Conversation Key -> Timestamp of the latest processed message leaf
    private val lastDeliveredTimestamps = ConcurrentHashMap<String, Long>()

    // Message signature cache to guard against rapid duplicate emissions within identical timestamps
    // Key: Signature ("$conversationKey-$timestamp-$text"), Value: Insertion epoch time
    private val processedSignatures = ConcurrentHashMap<String, Long>()

    fun evaluate(sbn: StatusBarNotification?): ProcessedNotification? {
        if (sbn == null || sbn.packageName != AppConstants.WHATSAPP_PACKAGE) return null

        val notification = sbn.notification ?: return null

        // 1. Ignore persistent/ongoing tasks (e.g., active backups, web sessions, ongoing calls)
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return null

        // 2. Ignore Android system-level group summary bundles ("X messages from Y chats")
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null

        val extras = notification.extras ?: return null

        // 3. Structural sub-summary check: WhatsApp chat summaries contain EXTRA_SUMMARY_TEXT
        if (extras.containsKey(Notification.EXTRA_SUMMARY_TEXT)) {
            return null
        }

        // 4. Style check: Chat summaries often fall back to InboxStyle or BigTextStyle.
        // True individual chat messages consistently use MessagingStyle.
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        if (template.isNotEmpty() && !template.contains("MessagingStyle")) {
            return null
        }

        // 5. Extract structured MessagingStyle payload defensively
        val messagingStyle = try {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        } catch (_: Throwable) {
            null
        } ?: return null

        if (messagingStyle.messages.isEmpty()) return null

        // Extract strictly the most recent message leaf node
        val latestMessage = messagingStyle.messages.last()
        val text = latestMessage.text?.toString().orEmpty().trim()
        val messageTime = latestMessage.timestamp

        // 6. Identity verification: Counter summaries lack an associated Person instance for direct chats
        if (latestMessage.person == null && !messagingStyle.isGroupConversation) {
            return null
        }

        if (text.isBlank()) return null

        // 7. Resolve conversation or sender title
        val senderName = latestMessage.person?.name?.toString().orEmpty().trim()
        val convTitle = messagingStyle.conversationTitle?.toString().orEmpty().trim()
        val fallbackTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()

        val rawTitle = resolveTitle(
            isGroupConversation = messagingStyle.isGroupConversation,
            conversationTitle = convTitle,
            senderName = senderName,
            fallbackTitle = fallbackTitle
        )

        if (rawTitle.isBlank()) return null

        // 8. Deduplication checkpoint: Use composite key (tag or id) to prevent multi-chat collisions
        val conversationKey = sbn.tag ?: sbn.id.toString()
        return process(
            conversationKey = conversationKey,
            rawTitle = rawTitle,
            text = text,
            messageTime = messageTime
        )
    }

    /**
     * Resolves the display title based on conversation type and available sender information.
     */
    fun resolveTitle(
        isGroupConversation: Boolean,
        conversationTitle: String,
        senderName: String,
        fallbackTitle: String = ""
    ): String {
        return when {
            isGroupConversation && conversationTitle.isNotBlank() && senderName.isNotBlank() ->
                "$conversationTitle ($senderName)"
            isGroupConversation && conversationTitle.isNotBlank() ->
                conversationTitle
            senderName.isNotBlank() ->
                senderName
            else ->
                fallbackTitle
        }.trim()
    }

    /**
     * Core deduplication engine: checks against sliding-window signature cache
     * and strictly enforces monotonic per-conversation timestamps.
     * Pure JVM method - completely unit-testable.
     */
    fun process(
        conversationKey: String,
        rawTitle: String,
        text: String,
        messageTime: Long,
        currentTimeMs: Long = System.currentTimeMillis()
    ): ProcessedNotification? {
        val signature = "$conversationKey-$messageTime-$text"

        // Prune stale signatures older than deduplication window
        processedSignatures.entries.removeIf { currentTimeMs - it.value > deduplicationWindowMs }

        if (processedSignatures.containsKey(signature)) {
            return null
        }

        val previousTimestamp = lastDeliveredTimestamps[conversationKey] ?: 0L
        if (messageTime < previousTimestamp) {
            return null
        }

        // Update tracking state
        processedSignatures[signature] = currentTimeMs
        lastDeliveredTimestamps[conversationKey] = messageTime

        return ProcessedNotification(
            title = "[WA] $rawTitle",
            text = text,
            timestamp = messageTime
        )
    }

    /**
     * Clears in-memory caches (useful for testing and memory resets).
     */
    fun reset() {
        lastDeliveredTimestamps.clear()
        processedSignatures.clear()
    }
}