package com.flightchat.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flightchat.model.AppSettings
import com.flightchat.model.ChatMessage
import com.flightchat.model.User

@Database(
    entities = [ChatMessage::class, User::class, AppSettings::class],
    version = 3,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {
    
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun userDao(): UserDao
    abstract fun appSettingsDao(): AppSettingsDao
    
    companion object {
        @Volatile
        private var instance: ChatDatabase? = null
        
        fun getInstance(context: Context): ChatDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "flightchat_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also {
                    instance = it
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Legacy records without a network ID receive a persistent local ID.
                db.execSQL("UPDATE messages SET messageId = 'legacy_' || lower(hex(randomblob(16))) WHERE messageId IS NULL OR messageId = ''")
                db.execSQL("DELETE FROM messages WHERE id NOT IN (SELECT MIN(id) FROM messages GROUP BY messageId)")
                db.execSQL("CREATE UNIQUE INDEX index_messages_messageId ON messages(messageId)")
                for (column in listOf("replyToId", "replyNickname", "replyPreview", "imageId")) {
                    db.execSQL("ALTER TABLE messages ADD COLUMN $column TEXT NOT NULL DEFAULT ''")
                }
                db.execSQL("ALTER TABLE messages ADD COLUMN deliveryState TEXT NOT NULL DEFAULT 'SENT'")
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_settings (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        nickname TEXT NOT NULL,
                        isHost INTEGER NOT NULL,
                        serverHost TEXT NOT NULL,
                        serverPort INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
