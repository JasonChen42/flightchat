package com.flightchat.network

import com.google.gson.Gson
import java.util.UUID
import com.flightchat.model.ChatMessage

/**
 * 网络消息协议处理
 */
object MessageProtocol {
    private val gson = Gson()
    
    fun encodeMessage(message: ChatMessage): String {
        return gson.toJson(message)
    }
    
    fun decodeMessage(json: String): ChatMessage? {
        return try {
            gson.fromJson(json.trim(), ChatMessage::class.java)?.let { message ->
                message.copy(replyToId = message.replyToId ?: "", replyNickname = message.replyNickname ?: "",
                    replyPreview = message.replyPreview ?: "", imageId = message.imageId ?: "",
                    deliveryState = message.deliveryState ?: "SENT")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun createUserJoinMessage(userId: String, nickname: String): ChatMessage {
        return ChatMessage(
            messageId = UUID.randomUUID().toString(),
            from = userId,
            to = "all",
            nickname = nickname,
            content = "$nickname 加入了聊天室",
            type = "USER_JOIN"
        )
    }

    fun createUserPresenceMessage(userId: String, nickname: String): ChatMessage {
        return ChatMessage(
            messageId = UUID.randomUUID().toString(),
            from = userId,
            to = "all",
            nickname = nickname,
            content = "$nickname 已在聊天室",
            type = "USER_PRESENT"
        )
    }
    
    fun createUserLeaveMessage(userId: String, nickname: String): ChatMessage {
        return ChatMessage(
            messageId = UUID.randomUUID().toString(),
            from = userId,
            to = "all",
            nickname = nickname,
            content = "$nickname 离开了聊天室",
            type = "USER_LEAVE"
        )
    }
    
    fun createChatMessage(
        userId: String,
        nickname: String,
        content: String,
        to: String = "all"
    ): ChatMessage {
        return ChatMessage(
            messageId = UUID.randomUUID().toString(),
            from = userId,
            to = to,
            nickname = nickname,
            content = content,
            type = "MESSAGE"
        )
    }
}
