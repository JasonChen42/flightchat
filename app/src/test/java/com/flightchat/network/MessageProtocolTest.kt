package com.flightchat.network

import com.flightchat.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MessageProtocolTest {

    @Test
    fun encodeAndDecodeMessageRoundTrips() {
        val message = ChatMessage(
            messageId = "message-1",
            from = "user-1",
            to = "all",
            nickname = "Alice",
            content = "Hello",
            timestamp = 1234L,
            type = "MESSAGE"
        )

        val decoded = MessageProtocol.decodeMessage(MessageProtocol.encodeMessage(message))

        assertNotNull(decoded)
        assertEquals(message.messageId, decoded?.messageId)
        assertEquals(message.from, decoded?.from)
        assertEquals(message.nickname, decoded?.nickname)
        assertEquals(message.content, decoded?.content)
        assertEquals(message.timestamp, decoded?.timestamp)
        assertEquals(message.type, decoded?.type)
    }

    @Test
    fun decodeInvalidJsonReturnsNull() {
        assertNull(MessageProtocol.decodeMessage("not-json"))
    }

    @Test
    fun createUserJoinMessageUsesExpectedTypeAndSender() {
        val message = MessageProtocol.createUserJoinMessage("user-2", "Bob")

        assertEquals("USER_JOIN", message.type)
        assertEquals("user-2", message.from)
        assertEquals("Bob", message.nickname)
        assertEquals("all", message.to)
    }

    @Test
    fun createUserPresenceMessageUsesPresenceType() {
        val message = MessageProtocol.createUserPresenceMessage("user-3", "Carol")

        assertEquals("USER_PRESENT", message.type)
        assertEquals("user-3", message.from)
        assertEquals("Carol", message.nickname)
        assertEquals("all", message.to)
    }
}
