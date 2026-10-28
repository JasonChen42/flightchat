package com.flightchat.server

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.flightchat.database.ChatDatabase
import com.flightchat.keepalive.BackgroundKeepAlive
import com.flightchat.model.ChatMessage
import com.flightchat.model.User
import com.flightchat.network.ChatDefaults
import com.flightchat.network.MessageProtocol
import com.flightchat.network.SyncProtocol
import com.flightchat.network.SyncFrame
import com.flightchat.network.ImageStore
import java.util.UUID
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.flightchat.notification.ChatNotificationManager
import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class ChatServerService : Service() {
    
    private var serverPort = ChatDefaults.DEFAULT_SERVER_PORT
    private var serverSocket: ServerSocket? = null
    private var mediaSocket: ServerSocket? = null
    private val mediaToken = UUID.randomUUID().toString()
    private val mediaSlots = Semaphore(3)
    private val serverId: String by lazy {
        val prefs = getSharedPreferences("sync_server", MODE_PRIVATE)
        prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("id", it).commit()
        }
    }
    private var serverJob: Job? = null
    private val clientHandlers = ConcurrentHashMap<String, ClientHandler>()
    private val onlineUsers = ConcurrentHashMap<String, User>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val binder = LocalBinder()
    private val database by lazy { ChatDatabase.getInstance(this) }
    private val keepAlive by lazy { BackgroundKeepAlive(this, TAG) }
    private val TAG = "ChatServerService"
    private var hostUserId: String = ""
    private var hostNickname: String = "Host"
    private var isServerRunning = false
    private var foregroundStarted = false
    private var stoppingServer = false
    private var connectionCallback: ((Boolean) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): ChatServerService = this@ChatServerService
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Server Service started")
        val userId = intent?.getStringExtra("userId").orEmpty()
        val nickname = intent?.getStringExtra("nickname").orEmpty()
        val port = ChatDefaults.normalizeServerPort(
            intent?.getIntExtra(
                "serverPort",
                ChatDefaults.DEFAULT_SERVER_PORT
            ) ?: ChatDefaults.DEFAULT_SERVER_PORT
        )
        if (userId.isNotBlank() && nickname.isNotBlank()) {
            ensureForeground()
            keepAlive.acquire()
            startAsHost(userId, nickname, port)
        }
        return START_REDELIVER_INTENT
    }

    fun setConnectionCallback(callback: ((Boolean) -> Unit)?) {
        connectionCallback = callback
        callback?.invoke(isServerRunning)
    }

    fun startAsHost(
        userId: String,
        nickname: String,
        port: Int = ChatDefaults.DEFAULT_SERVER_PORT
    ) {
        if (isServerRunning && serverPort != port) {
            stopServerSocket()
        }
        hostUserId = userId
        hostNickname = nickname
        serverPort = port
        clientHandlers.values.forEach { it.close() }
        clientHandlers.clear()
        onlineUsers.clear()
        val host = User(
            userId = userId,
            nickname = nickname,
            isOnline = true,
            isHost = true
        )
        onlineUsers[userId] = host
        scope.launch {
            database.userDao().deleteAll()
            database.userDao().insert(host)
        }
        startServer()
    }
    
    private fun startServer() {
        if (isServerRunning || serverJob?.isActive == true) return
        stoppingServer = false
        serverJob = scope.launch {
            try {
                val socket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress("0.0.0.0", serverPort))
                }
                serverSocket = socket
                mediaSocket = ServerSocket(0).also { media ->
                    launch {
                        while (isActive) {
                            val peer = media.accept()
                            launch {
                                mediaSlots.withPermit {
                                    runCatching { ImageStore.serve(this@ChatServerService, peer, mediaToken) }
                                    peer.close()
                                }
                            }
                        }
                    }
                }
                isServerRunning = true
                connectionCallback?.invoke(true)
                Log.d(TAG, "Server started on port $serverPort")
                
                while (scope.isActive) {
                    val clientSocket = socket.accept()
                    if (clientSocket != null) {
                        Log.d(TAG, "Client connected: ${clientSocket.inetAddress.hostAddress}")
                        val handler = ClientHandler(clientSocket, this@ChatServerService)
                        launch {
                            handler.run()
                        }
                    }
                }
            } catch (e: Exception) {
                if (scope.isActive && !stoppingServer) {
                    Log.e(TAG, "Server error: ${e.message}", e)
                }
            } finally {
                serverSocket?.close()
                serverSocket = null
                isServerRunning = false
                connectionCallback?.invoke(false)
            }
        }
    }

    private fun stopServerSocket() {
        stoppingServer = true
        serverJob?.cancel()
        serverSocket?.close()
        mediaSocket?.close()
        mediaSocket = null
        serverSocket = null
        serverJob = null
        isServerRunning = false
        connectionCallback?.invoke(false)
    }

    fun sendHostMessage(content: String, to: String = "all", reply: ChatMessage? = null, imageId: String = "") {
        if ((content.isBlank() && imageId.isBlank()) || hostUserId.isBlank()) return
        val message = MessageProtocol.createChatMessage(hostUserId, hostNickname, content, to).copy(
            replyToId = reply?.messageId.orEmpty(), replyNickname = reply?.nickname.orEmpty(),
            replyPreview = reply?.let { if (it.type == "IMAGE") "[图片] ${it.content}" else it.content.take(160) }.orEmpty(),
            imageId = imageId, type = if (imageId.isBlank()) "MESSAGE" else "IMAGE"
        )
        scope.launch {
            database.chatMessageDao().insert(message)
            broadcastMessage(message)
        }
    }
    
    /**
     * 广播消息给所有连接的客户端
     */
    fun broadcastMessage(message: ChatMessage, excludeUserId: String? = null) {
        scope.launch {
            val encoded = MessageProtocol.encodeMessage(message)
            clientHandlers.forEach { (userId, handler) ->
                if (excludeUserId == null || userId != excludeUserId) {
                    handler.sendMessage(encoded)
                }
            }
        }
    }

    private suspend fun registerClient(userId: String, nickname: String, handler: ClientHandler) {
        val user = User(
            userId = userId,
            nickname = nickname,
            isOnline = true,
            isHost = false
        )
        onlineUsers[userId] = user
        clientHandlers[userId] = handler
        database.userDao().insert(user)

        onlineUsers.values
            .filter { it.userId != userId && it.isOnline }
            .forEach { existingUser ->
                handler.sendMessage(
                    MessageProtocol.encodeMessage(
                        MessageProtocol.createUserPresenceMessage(
                            existingUser.userId,
                            existingUser.nickname
                        )
                    )
                )
            }

        val joinMsg = MessageProtocol.createUserJoinMessage(userId, nickname)
        database.chatMessageDao().insert(joinMsg)
        ChatNotificationManager.showUserJoined(this, joinMsg)
        broadcastMessage(joinMsg, excludeUserId = userId)
    }

    private suspend fun handleClientMessage(incoming: ChatMessage, fromUserId: String) {
        if (incoming.type !in setOf("MESSAGE", "IMAGE", "USER_JOIN", "USER_LEAVE")) return
        val message = incoming.copy(id = 0, from = fromUserId, deliveryState = "SENT")
        if (message.messageId.isBlank()) return
        if (message.type == "IMAGE" && !ImageStore.exists(this, message.imageId)) return
        if (message.type in setOf("MESSAGE", "IMAGE")) {
            val inserted = database.chatMessageDao().insert(message)
            clientHandlers[fromUserId]?.let { handler ->
                if (handler.modern) handler.sendMessage(SyncProtocol.encode(SyncFrame("ACK", messageId = message.messageId)))
            }
            if (inserted != -1L) {
                ChatNotificationManager.showMessage(this, message)
                broadcastMessage(message, excludeUserId = fromUserId)
            }
            return
        }
        when (message.type) {
            "USER_JOIN" -> {
                registerOrUpdateUser(message.from, message.nickname, isHost = false)
                if (message.from != hostUserId) {
                    ChatNotificationManager.showUserJoined(this, message)
                }
                broadcastMessage(message, excludeUserId = fromUserId)
            }
            "USER_LEAVE" -> {
                database.userDao().setOnline(message.from, false)
                onlineUsers[message.from]?.let {
                    onlineUsers[message.from] = it.copy(isOnline = false)
                }
                database.chatMessageDao().insert(message)
                broadcastMessage(message, excludeUserId = fromUserId)
            }
            else -> {
                database.chatMessageDao().insert(message)
                if (message.from != hostUserId) {
                    ChatNotificationManager.showMessage(this, message)
                }
                broadcastMessage(message, excludeUserId = fromUserId)
            }
        }
    }

    private suspend fun registerOrUpdateUser(userId: String, nickname: String, isHost: Boolean) {
        val user = User(
            userId = userId,
            nickname = nickname,
            isOnline = true,
            isHost = isHost
        )
        onlineUsers[userId] = user
        database.userDao().insert(user)
    }
    
    /**
     * 移除断开的客户端
     */
    fun removeClient(clientId: String) {
        if (clientId.isBlank()) return
        clientHandlers.remove(clientId)
        onlineUsers[clientId]?.let {
            onlineUsers[clientId] = it.copy(isOnline = false)
        }
        scope.launch {
            database.userDao().setOnline(clientId, false)
        }
        Log.d(TAG, "Client removed: $clientId, remaining: ${clientHandlers.size}")
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        ChatNotificationManager.startForegroundSession(
            service = this,
            notificationId = FOREGROUND_NOTIFICATION_ID,
            title = "FlightChat 主机在线",
            text = "热点聊天室后台运行中"
        )
        foregroundStarted = true
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Server destroyed")
        stopServerSocket()
        clientHandlers.values.forEach { it.close() }
        keepAlive.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        runBlocking(Dispatchers.IO) {
            database.userDao().setAllOffline()
        }
        scope.cancel()
        isServerRunning = false
        connectionCallback?.invoke(false)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed, keeping host foreground service alive")
        keepAlive.acquire()
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 3002
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    /**
     * 客户端处理器
     */
    inner class ClientHandler(
        private val socket: Socket,
        private val server: ChatServerService
    ) {
        var modern = false
        private var reader: BufferedReader? = null
        private var writer: PrintWriter? = null
        private var clientId: String = ""
        private var userId: String = ""
        private var nickname: String = "匿名用户"
        
        init {
            try {
                reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)
            } catch (e: Exception) {
                Log.e(TAG, "Handler init error: ${e.message}")
            }
        }
        
        suspend fun run() {
            try {
                // 读取客户端信息
                val firstLine = reader?.readLine()
                if (firstLine != null) {
                    val msg = MessageProtocol.decodeMessage(firstLine)
                    if (msg != null) {
                        userId = msg.from
                        nickname = msg.nickname
                        clientId = userId
                        
                        Log.d(TAG, "Client registered: $nickname ($userId)")
                        modern = msg.replyPreview == SyncProtocol.CAPABILITY
                        if (modern) sendMessage(SyncProtocol.encode(SyncFrame(
                            "HELLO", serverId = serverId, mediaPort = mediaSocket?.localPort ?: 0, token = mediaToken
                        )))
                        server.registerClient(userId, nickname, this)
                    }
                }
                
                // 持续读取消息
                while (scope.isActive) {
                    val line = reader?.readLine() ?: break
                    if (line.isNotEmpty()) {
                        val control = if (modern) SyncProtocol.decode(line) else null
                        if (control != null) {
                            when (control.type) {
                                "PING" -> sendMessage(SyncProtocol.encode(SyncFrame("PONG")))
                                "SYNC_REQUEST" -> {
                                    val through = database.chatMessageDao().maxId()
                                    var cursor = if (control.serverId == serverId) control.cursor.coerceIn(0, through) else 0
                                    while (true) {
                                        val page = database.chatMessageDao().history(cursor, through)
                                        if (page.isEmpty()) break
                                        cursor = page.last().id
                                        sendMessage(SyncProtocol.encode(SyncFrame("SYNC_PAGE", serverId = serverId,
                                            cursor = cursor, messages = page.map { it.copy(id = 0) })))
                                    }
                                    sendMessage(SyncProtocol.encode(SyncFrame("SYNC_END", serverId = serverId, cursor = through)))
                                }
                            }
                            continue
                        }
                        val message = MessageProtocol.decodeMessage(line)
                        if (message != null) {
                            Log.d(TAG, "Received message from $nickname: ${message.content}")
                            server.handleClientMessage(message, userId)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Handler run error: ${e.message}")
            } finally {
                // 用户离开消息
                if (userId.isNotEmpty() && clientHandlers[userId] === this) {
                    val leaveMsg = MessageProtocol.createUserLeaveMessage(userId, nickname)
                    database.chatMessageDao().insert(leaveMsg)
                    database.userDao().setOnline(userId, false)
                    server.broadcastMessage(leaveMsg, excludeUserId = userId)
                }
                if (clientHandlers[clientId] === this) this@ChatServerService.removeClient(clientId)
                close()
            }
        }
        
        @Synchronized
        fun sendMessage(message: String) {
            try {
                writer?.println(message)
                writer?.flush()
                if (writer?.checkError() == true) socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Send message error: ${e.message}")
            }
        }
        
        fun close() {
            try {
                socket.close()
                reader?.close()
                writer?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Close error: ${e.message}")
            }
        }
    }
}
