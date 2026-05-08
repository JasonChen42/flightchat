package com.flightchat.model

data class AppState(
    val isHost: Boolean = false,
    val isConnected: Boolean = false,
    val currentUserId: String = "",
    val currentNickname: String = "",
    val serverPort: Int = 5555,
    val serverHost: String = "auto",
    val connectedUsers: List<User> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val errorMessage: String? = null
)
