package com.flightchat.database

import androidx.room.*
import com.flightchat.model.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: ChatMessage): Long

    @Query("SELECT * FROM (SELECT * FROM messages ORDER BY timestamp DESC, id DESC LIMIT :limit) ORDER BY timestamp ASC, id ASC")
    fun getAllMessages(limit: Int = 100): Flow<List<ChatMessage>>

    @Query("SELECT * FROM messages WHERE `to` = :to ORDER BY timestamp DESC LIMIT 50")
    fun getMessagesByRecipient(to: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM messages WHERE type IN ('MESSAGE', 'IMAGE') AND instr(lower(content), lower(:query)) > 0 ORDER BY timestamp DESC, id DESC LIMIT 200")
    fun search(query: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    suspend fun find(messageId: String): ChatMessage?

    @Query("SELECT COUNT(*) FROM messages WHERE timestamp >= :timestamp")
    suspend fun countSince(timestamp: Long): Int

    @Query("SELECT COALESCE(MAX(id), 0) FROM messages")
    suspend fun maxId(): Long

    @Query("SELECT * FROM messages WHERE id > :after AND id <= :through AND deliveryState = 'SENT' ORDER BY id ASC LIMIT 100")
    suspend fun history(after: Long, through: Long): List<ChatMessage>

    @Query("SELECT * FROM messages WHERE deliveryState = 'PENDING' AND `from` = :userId ORDER BY id ASC")
    suspend fun pending(userId: String): List<ChatMessage>

    @Query("UPDATE messages SET deliveryState = 'SENT' WHERE messageId = :messageId")
    suspend fun acknowledge(messageId: String)

    @Query("SELECT DISTINCT imageId FROM messages WHERE imageId != '' AND deliveryState = 'SENT'")
    suspend fun imageIds(): List<String>

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getMessageCount(): Int
}
