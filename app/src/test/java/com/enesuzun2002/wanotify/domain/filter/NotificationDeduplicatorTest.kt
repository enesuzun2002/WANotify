package com.enesuzun2002.wanotify.domain.filter

import com.enesuzun2002.wanotify.domain.model.ProcessedNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class NotificationDeduplicatorTest {

    private lateinit var deduplicator: NotificationDeduplicator

    @Before
    fun setUp() {
        deduplicator = NotificationDeduplicator(deduplicationWindowMs = 10_000L)
    }

    @Test
    fun `first message from conversation is processed successfully`() {
        val result = deduplicator.process(
            conversationKey = "chat_123",
            rawTitle = "Alice",
            text = "Hello there!",
            messageTime = 1000L,
            currentTimeMs = 1000L
        )

        assertNotNull(result)
        assertEquals("[WA] Alice", result?.title)
        assertEquals("Hello there!", result?.text)
        assertEquals(1000L, result?.timestamp)
    }

    @Test
    fun `identical message within window is rejected as duplicate`() {
        deduplicator.process(
            conversationKey = "chat_123",
            rawTitle = "Alice",
            text = "Hello there!",
            messageTime = 1000L,
            currentTimeMs = 1000L
        )

        // Rapid duplicate notification trigger (common in WhatsApp message updates)
        val duplicate = deduplicator.process(
            conversationKey = "chat_123",
            rawTitle = "Alice",
            text = "Hello there!",
            messageTime = 1000L,
            currentTimeMs = 1500L
        )

        assertNull("Duplicate message within 10s window must be rejected", duplicate)
    }

    @Test
    fun `stale message with older timestamp than last delivered is rejected`() {
        deduplicator.process(
            conversationKey = "chat_123",
            rawTitle = "Alice",
            text = "Second message",
            messageTime = 5000L,
            currentTimeMs = 5000L
        )

        // Stale or out-of-order historical message leaf
        val stale = deduplicator.process(
            conversationKey = "chat_123",
            rawTitle = "Alice",
            text = "First message received late",
            messageTime = 4000L,
            currentTimeMs = 5100L
        )

        assertNull("Historical message with older timestamp must be dropped", stale)
    }

    @Test
    fun `new message with subsequent timestamp in same conversation is accepted`() {
        deduplicator.process(
            conversationKey = "chat_123",
            rawTitle = "Alice",
            text = "First",
            messageTime = 1000L,
            currentTimeMs = 1000L
        )

        val next = deduplicator.process(
            conversationKey = "chat_123",
            rawTitle = "Alice",
            text = "Second",
            messageTime = 2000L,
            currentTimeMs = 2000L
        )

        assertNotNull(next)
        assertEquals("Second", next?.text)
    }

    @Test
    fun `independent conversations do not collide or block each other`() {
        val chat1Result = deduplicator.process(
            conversationKey = "chat_alice",
            rawTitle = "Alice",
            text = "Hey!",
            messageTime = 1000L,
            currentTimeMs = 1000L
        )

        val chat2Result = deduplicator.process(
            conversationKey = "chat_bob",
            rawTitle = "Bob",
            text = "Hey!",
            messageTime = 1000L,
            currentTimeMs = 1005L
        )

        assertNotNull("Chat 1 message should be accepted", chat1Result)
        assertNotNull("Chat 2 message with same text/timestamp must be accepted independently", chat2Result)
        assertEquals("[WA] Alice", chat1Result?.title)
        assertEquals("[WA] Bob", chat2Result?.title)
    }

    @Test
    fun `expired signature after window threshold allows identical message`() {
        deduplicator.process(
            conversationKey = "chat_123",
            rawTitle = "Alice",
            text = "Ping",
            messageTime = 1000L,
            currentTimeMs = 1000L
        )

        // 15 seconds later (window is 10s), user sends "Ping" again with newer timestamp
        val repeated = deduplicator.process(
            conversationKey = "chat_123",
            rawTitle = "Alice",
            text = "Ping",
            messageTime = 16000L,
            currentTimeMs = 16000L
        )

        assertNotNull("Identical text after window with newer timestamp must be accepted", repeated)
    }

    @Test
    fun `resolveTitle formats group conversation with sender correctly`() {
        val title = deduplicator.resolveTitle(
            isGroupConversation = true,
            conversationTitle = "Android Dev Team",
            senderName = "John"
        )
        assertEquals("Android Dev Team (John)", title)
    }

    @Test
    fun `resolveTitle formats group conversation without sender fallback`() {
        val title = deduplicator.resolveTitle(
            isGroupConversation = true,
            conversationTitle = "Android Dev Team",
            senderName = ""
        )
        assertEquals("Android Dev Team", title)
    }

    @Test
    fun `resolveTitle formats direct conversation sender correctly`() {
        val title = deduplicator.resolveTitle(
            isGroupConversation = false,
            conversationTitle = "",
            senderName = "Alice Smith"
        )
        assertEquals("Alice Smith", title)
    }

    @Test
    fun `processedNotification stableId generates consistent non-zero hash`() {
        val notification1 = ProcessedNotification(
            title = "[WA] Alice",
            text = "Hello",
            timestamp = 1000L
        )
        val notification2 = ProcessedNotification(
            title = "[WA] Alice",
            text = "Hello",
            timestamp = 1000L
        )
        val notification3 = ProcessedNotification(
            title = "[WA] Alice",
            text = "Different",
            timestamp = 1000L
        )

        assertEquals(notification1.stableId, notification2.stableId)
        org.junit.Assert.assertNotEquals(notification1.stableId, notification3.stableId)
    }

    @Test
    fun `reset clears tracking caches and permits previously dropped duplicates`() {
        deduplicator.process(
            conversationKey = "chat_123",
            rawTitle = "Alice",
            text = "Hello",
            messageTime = 1000L,
            currentTimeMs = 1000L
        )

        // Attempt duplicate - should be dropped
        val duplicateBeforeReset = deduplicator.process(
            conversationKey = "chat_123",
            rawTitle = "Alice",
            text = "Hello",
            messageTime = 1000L,
            currentTimeMs = 1500L
        )
        assertNull("Duplicate should be rejected before reset", duplicateBeforeReset)

        // Reset caches
        deduplicator.reset()

        // Same message should now be accepted after cache reset
        val afterReset = deduplicator.process(
            conversationKey = "chat_123",
            rawTitle = "Alice",
            text = "Hello",
            messageTime = 1000L,
            currentTimeMs = 1500L
        )
        assertNotNull("Message should be accepted after reset clears state", afterReset)
    }
}
