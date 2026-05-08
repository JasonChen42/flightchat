package com.flightchat.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.flightchat.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<User>)
    
    @Update
    suspend fun update(user: User)
    
    @Query("SELECT * FROM users WHERE isOnline = 1 ORDER BY joinedAt ASC")
    fun getOnlineUsers(): Flow<List<User>>
    
    @Query("SELECT * FROM users ORDER BY joinedAt DESC")
    fun getAllUsers(): Flow<List<User>>
    
    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUserById(userId: String): User?

    @Query("UPDATE users SET isOnline = :isOnline WHERE userId = :userId")
    suspend fun setOnline(userId: String, isOnline: Boolean)

    @Query("UPDATE users SET isOnline = 0")
    suspend fun setAllOffline()
    
    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUser(userId: String)
    
    @Query("DELETE FROM users")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM users WHERE isOnline = 1")
    suspend fun getOnlineUserCount(): Int
}
