package com.flightchat.client

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.flightchat.database.ChatDatabase
import com.flightchat.keepalive.BackgroundKeepAlive
import com.flightchat.model.ChatMessage
import com.flightchat.model.User
import com.flightchat.network.MessageProtocol
import com.flightchat.notification.ChatNotificationManager
import kotlinx.coroutines.*
import java.io.*
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ChatClientService : Service() {
    
    private val TAG = "ChatClientService"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val binder = LocalBinder()
    private val database by lazy { ChatDatabase.getInstance(this) }
    private val keepAlive by lazy { BackgroundKeepAlive(this, TAG) }
    private val preferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    private var isConnected = false
    private var connectionJob: Job? = null
    
    private var userId: String = ""
    private var nickname: String = ""
    private var serverHost: String = AUTO_SERVER_HOST
    private var autoConnectMode = true
    private var serverPort: Int = 5555
    
    private var messageCallback: ((ChatMessage) -> Unit)? = null
    private var connectionCallback: ((Boolean) -> Unit)? = null
    private var leaveSent = false
    private var foregroundStarted = false

    inner class LocalBinder : Binder() {
        fun getService(): ChatClientService = this@ChatClientService
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Client Service started")
        
        userId = intent?.getStringExtra("userId") ?: "user_${System.currentTimeMillis()}"
        nickname = intent?.getStringExtra("nickname") ?: "Anonymous"
        serverHost = (intent?.getStringExtra("serverHost") ?: AUTO_SERVER_HOST).trim()
        autoConnectMode = serverHost.isAutoHost()
        serverPort = intent?.getIntExtra("serverPort", 5555) ?: 5555
        ensureForeground()
        keepAlive.acquire()
        
        connectToServer()
        return START_REDELIVER_INTENT
    }

    fun connect(userId: String, nickname: String, serverHost: String, serverPort: Int) {
        this.userId = userId
        this.nickname = nickname
        this.serverHost = serverHost.trim()
        this.autoConnectMode = this.serverHost.isAutoHost()
        this.serverPort = serverPort
        connectToServer()
    }
    
    /**
     * 连接到服务器
     */
    private fun connectToServer() {
        if (isConnected || connectionJob?.isActive == true) return
        connectionJob = scope.launch {
            while (isActive) {
                try {
                    connectAndListen()
                } catch (e: Exception) {
                    Log.e(TAG, "Connection error: ${e.message}")
                    isConnected = false
                    connectionCallback?.invoke(false)
                } finally {
                    disconnect()
                }

                if (isActive) {
                    delay(RECONNECT_DELAY_MS)
                }
            }
        }
    }

    private suspend fun connectAndListen() {
        leaveSent = false
        database.userDao().deleteAll()
        Log.d(TAG, "Connecting to ${describeServerTarget()}:$serverPort")
        val connectedSocket = openServerSocket()
        socket = connectedSocket
        reader = BufferedReader(InputStreamReader(socket?.getInputStream()))
        writer = PrintWriter(OutputStreamWriter(socket?.getOutputStream()), true)
        rememberServerHost(serverHost)

        Log.d(TAG, "Connected, sending user info")

        val userMsg = MessageProtocol.createUserJoinMessage(userId, nickname)
        val encoded = MessageProtocol.encodeMessage(userMsg)
        writer?.println(encoded)
        writer?.flush()

        database.userDao().insert(
            User(
                userId = userId,
                nickname = nickname,
                isOnline = true,
                isHost = false
            )
        )

        isConnected = true
        connectionCallback?.invoke(true)

        while (currentCoroutineContext().isActive) {
            val line = reader?.readLine() ?: break
            if (line.isNotEmpty()) {
                val message = MessageProtocol.decodeMessage(line)
                if (message != null) {
                    Log.d(TAG, "Received: ${message.content}")
                    persistIncomingMessage(message)
                    messageCallback?.invoke(message)
                }
            }
        }
    }

    private suspend fun openServerSocket(): Socket {
        val candidates = getServerHostCandidates()
        var lastError: Exception? = null

        for (host in candidates) {
            try {
                return connectToHost(host, CONNECTION_TIMEOUT_MS)
            } catch (e: Exception) {
                lastError = e
                Log.d(TAG, "Server $host:$serverPort unavailable: ${e.message}")
            }
        }

        if (autoConnectMode) {
            val discoveredHost = discoverServerHostInLocalSubnet()
            if (!discoveredHost.isNullOrBlank()) {
                try {
                    return connectToHost(discoveredHost, CONNECTION_TIMEOUT_MS)
                } catch (e: Exception) {
                    lastError = e
                    Log.d(TAG, "Discovered server $discoveredHost:$serverPort unavailable: ${e.message}")
                }
            }
        }

        throw IOException(
            "Unable to connect to server candidates: ${candidates.joinToString()}",
            lastError
        )
    }

    private fun connectToHost(host: String, timeoutMs: Int): Socket {
        Log.d(TAG, "Trying server $host:$serverPort")
        return Socket().apply {
            connect(InetSocketAddress(host, serverPort), timeoutMs)
            this@ChatClientService.serverHost = host
        }
    }

    private fun getServerHostCandidates(): List<String> {
        if (!autoConnectMode) return listOf(serverHost)

        return buildList {
            if (!serverHost.isAutoHost()) add(serverHost)
            getRememberedServerHost()?.let { add(it) }
            resolveDefaultGatewayHost()?.let { add(it) }
            addAll(COMMON_HOTSPOT_GATEWAYS)
        }.distinct()
    }

    private suspend fun discoverServerHostInLocalSubnet(): String? = withContext(Dispatchers.IO) {
        val localIp = resolveLocalIpv4Host() ?: return@withContext null
        val split = localIp.split(".")
        if (split.size != 4) return@withContext null

        val ownSuffix = split[3].toIntOrNull() ?: return@withContext null
        val prefix = split.take(3).joinToString(".")
        val gatewaySuffix = resolveDefaultGatewayHost()
            ?.substringAfterLast(".")
            ?.toIntOrNull()

        val candidates = (1..254).asSequence()
            .filter { it != ownSuffix && it != gatewaySuffix }
            .map { "$prefix.$it" }
            .toList()

        if (candidates.isEmpty()) return@withContext null

        Log.d(TAG, "Scanning local subnet $prefix.0/24 for server:$serverPort")
        val executor = Executors.newFixedThreadPool(SUBNET_SCAN_PARALLELISM)
        try {
            val tasks = candidates.map { host ->
                Callable<String> {
                    if (canConnect(host, serverPort, SUBNET_CONNECT_TIMEOUT_MS)) {
                        host
                    } else {
                        throw IOException("Port closed on $host")
                    }
                }
            }
            val host = executor.invokeAny(
                tasks,
                SUBNET_SCAN_TOTAL_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
            )
            Log.d(TAG, "Discovered candidate server on $host:$serverPort")
            host
        } catch (_: TimeoutException) {
            null
        } catch (_: ExecutionException) {
            null
        } catch (_: InterruptedException) {
            null
        } finally {
            executor.shutdownNow()
        }
    }

    private fun canConnect(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveDefaultGatewayHost(): String? {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && connectivityManager != null) {
            val activeNetwork = connectivityManager.activeNetwork
            val linkProperties = activeNetwork?.let { connectivityManager.getLinkProperties(it) }
            val gateway = linkProperties
                ?.routes
                ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                ?.gateway
                ?.hostAddress

            if (!gateway.isNullOrBlank()) return gateway
        }

        return resolveDhcpGatewayHost()
    }

    private fun resolveDhcpGatewayHost(): String? {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val gateway = wifiManager?.dhcpInfo?.gateway ?: return null
        if (gateway == 0) return null

        return InetAddress.getByAddress(
            byteArrayOf(
                (gateway and 0xff).toByte(),
                (gateway shr 8 and 0xff).toByte(),
                (gateway shr 16 and 0xff).toByte(),
                (gateway shr 24 and 0xff).toByte()
            )
        ).hostAddress
    }

    private fun resolveLocalIpv4Host(): String? {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && connectivityManager != null) {
            val activeNetwork = connectivityManager.activeNetwork
            val linkProperties = activeNetwork?.let { connectivityManager.getLinkProperties(it) }
            val linkAddress = linkProperties
                ?.linkAddresses
                ?.firstOrNull { it.address is Inet4Address }
                ?.address
                ?.hostAddress

            if (!linkAddress.isNullOrBlank()) return linkAddress
        }

        return null
    }

    private fun rememberServerHost(host: String) {
        if (host.isBlank() || host.isAutoHost()) return
        preferences.edit().putString(KEY_LAST_SERVER_HOST, host).apply()
    }

    private fun getRememberedServerHost(): String? {
        val host = preferences.getString(KEY_LAST_SERVER_HOST, null)?.trim().orEmpty()
        return host.takeIf { it.isNotBlank() && !it.isAutoHost() }
    }

    private fun describeServerTarget(): String =
        if (autoConnectMode) "auto discovery (gateway + cached host + subnet scan)" else serverHost

    private fun String.isAutoHost(): Boolean =
        isBlank() || equals(AUTO_SERVER_HOST, ignoreCase = true)
    
    /**
     * 发送消息
     */
    fun sendMessage(content: String, to: String = "all") {
        if (content.isBlank()) return
        scope.launch {
            try {
                val activeWriter = writer
                if (!isConnected || activeWriter == null) {
                    connectionCallback?.invoke(false)
                    return@launch
                }
                val message = MessageProtocol.createChatMessage(userId, nickname, content, to)
                val encoded = MessageProtocol.encodeMessage(message)
                database.chatMessageDao().insert(message)
                activeWriter.println(encoded)
                activeWriter.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Send error: ${e.message}")
            }
        }
    }
    
    /**
     * 设置消息回调
     */
    fun setMessageCallback(callback: (ChatMessage) -> Unit) {
        messageCallback = callback
    }
    
    /**
     * 设置连接状态回调
     */
    fun setConnectionCallback(callback: (Boolean) -> Unit) {
        connectionCallback = callback
        callback(isConnected)
    }

    fun clearCallbacks() {
        messageCallback = null
        connectionCallback = null
    }

    private suspend fun persistIncomingMessage(message: ChatMessage) {
        when (message.type) {
            "USER_JOIN" -> {
                database.userDao().insert(
                    User(
                        userId = message.from,
                        nickname = message.nickname,
                        isOnline = true,
                        isHost = false
                    )
                )
                database.chatMessageDao().insert(message)
                if (message.from != userId) {
                    ChatNotificationManager.showUserJoined(this, message)
                }
            }
            "USER_PRESENT" -> {
                database.userDao().insert(
                    User(
                        userId = message.from,
                        nickname = message.nickname,
                        isOnline = true,
                        isHost = false
                    )
                )
                database.chatMessageDao().insert(message)
                if (message.from != userId) {
                    ChatNotificationManager.showUserJoined(this, message)
                }
            }
            "USER_LEAVE" -> {
                database.userDao().setOnline(message.from, false)
                database.chatMessageDao().insert(message)
            }
            else -> {
                database.chatMessageDao().insert(message)
                if (message.from != userId) {
                    ChatNotificationManager.showMessage(this, message)
                }
            }
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        try {
            sendLeaveMessageIfNeeded()
            writer?.close()
            reader?.close()
            socket?.close()
            socket = null
            reader = null
            writer = null
            if (userId.isNotBlank()) {
                scope.launch {
                    database.userDao().setOnline(userId, false)
                }
            }
            if (isConnected) {
                isConnected = false
                connectionCallback?.invoke(false)
            }
            Log.d(TAG, "Disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error: ${e.message}")
        }
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        ChatNotificationManager.startForegroundSession(
            service = this,
            notificationId = FOREGROUND_NOTIFICATION_ID,
            title = "FlightChat 客户端在线",
            text = "后台保持连接，不会自动退出聊天室"
        )
        foregroundStarted = true
    }

    private fun sendLeaveMessageIfNeeded() {
        val activeWriter = writer
        if (!isConnected || leaveSent || activeWriter == null || userId.isBlank()) return
        try {
            activeWriter.println(
                MessageProtocol.encodeMessage(
                    MessageProtocol.createUserLeaveMessage(userId, nickname)
                )
            )
            activeWriter.flush()
            leaveSent = true
        } catch (e: Exception) {
            Log.e(TAG, "Send leave error: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Client destroyed")
        disconnect()
        keepAlive.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        runBlocking(Dispatchers.IO) {
            if (userId.isNotBlank()) {
                database.userDao().setOnline(userId, false)
            } else {
                database.userDao().setAllOffline()
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed, keeping client foreground service alive")
        keepAlive.acquire()
        super.onTaskRemoved(rootIntent)
    }
    
    override fun onBind(intent: Intent?): IBinder = binder

    companion object {
        const val AUTO_SERVER_HOST = "auto"
        private const val CONNECTION_TIMEOUT_MS = 1500
        private const val RECONNECT_DELAY_MS = 2000L
        private const val FOREGROUND_NOTIFICATION_ID = 3001
        private const val PREFS_NAME = "flightchat_client"
        private const val KEY_LAST_SERVER_HOST = "last_server_host"
        private const val SUBNET_CONNECT_TIMEOUT_MS = 180
        private const val SUBNET_SCAN_TOTAL_TIMEOUT_MS = 3000L
        private const val SUBNET_SCAN_PARALLELISM = 40
        private val COMMON_HOTSPOT_GATEWAYS = listOf(
            "192.168.49.1",
            "192.168.43.1",
            "172.20.10.1"
        )
    }
}
