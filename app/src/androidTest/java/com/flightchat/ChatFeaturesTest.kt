package com.flightchat

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flightchat.database.ChatDatabase
import com.flightchat.model.ChatMessage
import com.flightchat.network.ImageStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ChatFeaturesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun migrationPreservesHistoryAndDeduplicatesNetworkIds() = runBlocking {
        val name = "migration-${UUID.randomUUID()}"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, messageId TEXT NOT NULL, `from` TEXT NOT NULL, `to` TEXT NOT NULL, nickname TEXT NOT NULL, content TEXT NOT NULL, timestamp INTEGER NOT NULL, type TEXT NOT NULL)")
            old.execSQL("CREATE TABLE users (userId TEXT PRIMARY KEY NOT NULL, nickname TEXT NOT NULL, joinedAt INTEGER NOT NULL, isOnline INTEGER NOT NULL, isHost INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE app_settings (id TEXT PRIMARY KEY NOT NULL, userId TEXT NOT NULL, nickname TEXT NOT NULL, isHost INTEGER NOT NULL, serverHost TEXT NOT NULL, serverPort INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            old.execSQL("INSERT INTO messages VALUES (1, 'same', 'u', 'all', '名字', '历史记录', 1, 'MESSAGE')")
            old.execSQL("INSERT INTO messages VALUES (2, 'same', 'u', 'all', '名字', '历史记录', 1, 'MESSAGE')")
            old.execSQL("INSERT INTO messages VALUES (3, '', 'u', 'all', '名字', '旧记录', 2, 'MESSAGE')")
            old.version = 2
        }
        val db = Room.databaseBuilder(context, ChatDatabase::class.java, name)
            .addMigrations(ChatDatabase.MIGRATION_2_3).build()
        try {
            assertEquals(2, db.chatMessageDao().getMessageCount())
            assertEquals("历史记录", db.chatMessageDao().find("same")!!.content)
            assertEquals("", db.chatMessageDao().find("same")!!.replyToId)
            assertEquals(-1L, db.chatMessageDao().insert(ChatMessage(messageId = "same")))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun pagedReplayClosesGapsEvenWhenLiveMessageArrivesFirst() = runBlocking {
        val host = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java).build()
        val client = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java).build()
        try {
            for (i in 1..235) host.chatMessageDao().insert(ChatMessage(messageId = "m$i", content = "消息 $i", timestamp = 1000L - i))
            // Live delivery can beat history and clocks can run backwards.
            client.chatMessageDao().insert(host.chatMessageDao().find("m235")!!.copy(id = 0))
            var cursor = 0L
            val through = host.chatMessageDao().maxId()
            while (true) {
                val page = host.chatMessageDao().history(cursor, through)
                if (page.isEmpty()) break
                page.forEach { client.chatMessageDao().insert(it.copy(id = 0)) }
                // Simulate disconnect after commit but before saving the cursor: replay the page.
                page.forEach { client.chatMessageDao().insert(it.copy(id = 0)) }
                cursor = page.last().id
            }
            assertEquals(235, client.chatMessageDao().getMessageCount())
            assertEquals(100, client.chatMessageDao().getAllMessages().first().size)
            assertEquals(235, client.chatMessageDao().getAllMessages(300).first().size)
            assertEquals(1, client.chatMessageDao().search("消息 235").first().size)
            client.chatMessageDao().insert(ChatMessage(messageId = "pending", from = "me", deliveryState = "PENDING"))
            assertEquals(1, client.chatMessageDao().pending("me").size)
            client.chatMessageDao().acknowledge("pending")
            assertTrue(client.chatMessageDao().pending("me").isEmpty())
        } finally { host.close(); client.close() }
    }

    @Test fun imageDownloadVerifiesContentAndSurvivesLocalReload() {
        val bitmap = Bitmap.createBitmap(13, 17, Bitmap.Config.ARGB_8888)
        val bytes = java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val id = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val pool = Executors.newSingleThreadExecutor()
        val listener = ServerSocket(0)
        val future = pool.submit {
            listener.accept().use { socket ->
                val input = java.io.DataInputStream(socket.getInputStream())
                assertEquals("secret", input.readUTF())
                assertEquals("GET", input.readUTF())
                assertEquals(id, input.readUTF())
                val output = java.io.DataOutputStream(socket.getOutputStream())
                output.writeLong(bytes.size.toLong()); output.write(bytes); output.flush()
            }
        }
        try {
            ImageStore.transfer(context, "127.0.0.1", listener.localPort, "secret", id, false)
            future.get(5, TimeUnit.SECONDS)
            assertArrayEquals(bytes, ImageStore.file(context, id).readBytes())
            assertEquals(13, ImageStore.decode(ImageStore.file(context, id).path)!!.width)
            try {
                ImageStore.receive(context, id, 3, java.io.DataInputStream(byteArrayOf(1, 2, 3).inputStream()))
                fail("Corrupt image accepted")
            } catch (_: IllegalArgumentException) { }
            assertArrayEquals(bytes, ImageStore.file(context, id).readBytes())
        } finally { listener.close(); pool.shutdownNow(); ImageStore.file(context, id).delete() }
    }
}
