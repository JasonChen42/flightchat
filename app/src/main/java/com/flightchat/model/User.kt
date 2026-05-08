package com.flightchat.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户信息数据模型
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val userId: String = "",
    
    val nickname: String = "",
    
    val joinedAt: Long = System.currentTimeMillis(),
    
    val isOnline: Boolean = false,
    
    val isHost: Boolean = false
)
