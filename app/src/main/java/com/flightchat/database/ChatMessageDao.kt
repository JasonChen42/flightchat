package com.flightchat.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.flightchat.model.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    
    @Insert
    suspend fun insert(message: ChatMessage)
    
    @Query("SELECT * FROM (SELECT * FROM messages ORDER BY timestamp DESC LIMIT 100) ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>
    
    @Query("SELECT * FROM messages WHERE `to` = :to ORDER BY timestamp DESC LIMIT 50")
    fun getMessagesByRecipient(to: String): Flow<List<ChatMessage>>
    
    @Query("DELETE FROM messages")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getMessageCount(): Int
}
