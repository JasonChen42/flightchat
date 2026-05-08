package com.flightchat.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.flightchat.model.ChatMessage
import com.flightchat.model.User

@Database(
    entities = [ChatMessage::class, User::class],
    version = 1,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {
    
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun userDao(): UserDao
    
    companion object {
        @Volatile
        private var instance: ChatDatabase? = null
        
        fun getInstance(context: Context): ChatDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "flightchat_db"
                ).build().also {
                    instance = it
                }
            }
        }
    }
}
