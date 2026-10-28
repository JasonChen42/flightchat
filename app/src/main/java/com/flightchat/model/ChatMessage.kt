package com.flightchat.model

import androidx.room.Index
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * 聊天消息数据模型
 */
@Entity(tableName = "messages", indices = [Index(value = ["messageId"], unique = true)])
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @SerializedName("messageId")
    val messageId: String = "",
    
    @SerializedName("from")
    val from: String = "",
    
    @SerializedName("to")
    val to: String = "all",
    
    @SerializedName("nickname")
    val nickname: String = "",
    
    @SerializedName("content")
    val content: String = "",
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @SerializedName("type")
    val type: String = "MESSAGE",
    val replyToId: String = "",
    val replyNickname: String = "",
    val replyPreview: String = "",
    val imageId: String = "",
    val deliveryState: String = "SENT"

)
