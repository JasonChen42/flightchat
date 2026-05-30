package com.flightchat.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.flightchat.client.ChatClientService
import com.flightchat.network.ChatDefaults

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: String = DEFAULT_ID,
    val userId: String = "",
    val nickname: String = "",
    val isHost: Boolean = false,
    val serverHost: String = ChatClientService.AUTO_SERVER_HOST,
    val serverPort: Int = ChatDefaults.DEFAULT_SERVER_PORT,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_ID = "default"
    }
}
