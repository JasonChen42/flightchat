package com.flightchat.network

import com.flightchat.model.ChatMessage
import com.google.gson.Gson

/** Control frames are only exchanged after the v2 capability handshake. */
data class SyncFrame(
    val type: String,
    val serverId: String = "",
    val cursor: Long = 0,
    val messages: List<ChatMessage> = emptyList(),
    val messageId: String = "",
    val mediaPort: Int = 0,
    val token: String = ""
)

object SyncProtocol {
    const val CAPABILITY = "flightchat/2"
    private val gson = Gson()
    fun encode(frame: SyncFrame): String = gson.toJson(frame)
    fun decode(line: String): SyncFrame? = try {
        val frame = gson.fromJson(line, SyncFrame::class.java)
        frame?.takeIf { it.type in setOf("HELLO", "SYNC_REQUEST", "SYNC_PAGE", "SYNC_END", "ACK", "PING", "PONG") }
    } catch (_: Exception) { null }
}
