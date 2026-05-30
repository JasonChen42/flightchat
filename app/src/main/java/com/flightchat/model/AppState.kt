package com.flightchat.model

import com.flightchat.network.ChatDefaults

data class AppState(
    val isHost: Boolean = false,
    val isConnected: Boolean = false,
    val currentUserId: String = "",
    val currentNickname: String = "",
    val serverPort: Int = ChatDefaults.DEFAULT_SERVER_PORT,
    val serverHost: String = "auto",
    val connectedUsers: List<User> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val errorMessage: String? = null
)
