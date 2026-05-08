package com.flightchat

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.flightchat.client.ChatClientService
import com.flightchat.database.ChatDatabase
import com.flightchat.model.AppState
import com.flightchat.model.User
import com.flightchat.notification.ChatNotificationManager
import com.flightchat.server.ChatServerService
import com.flightchat.ui.ChatScreen
import com.flightchat.ui.StartScreen

class MainActivity : ComponentActivity() {
    
    private val database by lazy { ChatDatabase.getInstance(this) }
    private var clientService: ChatClientService? = null
    private var serverService: ChatServerService? = null
    private var clientBound = false
    private var serverBound = false
    private var connectionStateCallback: ((Boolean) -> Unit)? = null
    private val sessionPrefs by lazy {
        getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val serverConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serverService = (service as ChatServerService.LocalBinder).getService()
            serverBound = true
            serverService?.setConnectionCallback { connected ->
                runOnUiThread {
                    connectionStateCallback?.invoke(connected)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serverService?.setConnectionCallback(null)
            serverService = null
            serverBound = false
            connectionStateCallback?.invoke(false)
        }
    }

    private val clientConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            clientService = (service as ChatClientService.LocalBinder).getService()
            clientBound = true
            clientService?.setConnectionCallback { connected ->
                runOnUiThread {
                    connectionStateCallback?.invoke(connected)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            clientService?.clearCallbacks()
            clientService = null
            clientBound = false
            connectionStateCallback?.invoke(false)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChatNotificationManager.ensureChannel(this)
        requestNotificationPermissionIfNeeded()

        var restoredSession = resolveRestorableSession()
        if (restoredSession != null) {
            val bound = if (restoredSession.isHost) {
                bindRunningServerService()
            } else {
                bindRunningClientService()
            }
            if (!bound) {
                clearPersistedSession()
                restoredSession = null
            }
        }
        
        setContent {
            val initialSession = remember { restoredSession }
            var appState by remember {
                mutableStateOf(
                    AppState(
                        isHost = initialSession?.isHost ?: false,
                        currentUserId = initialSession?.userId ?: "user_${System.currentTimeMillis()}",
                        currentNickname = initialSession?.nickname.orEmpty(),
                        serverPort = initialSession?.serverPort ?: 5555,
                        serverHost = initialSession?.serverHost ?: ChatClientService.AUTO_SERVER_HOST
                    )
                )
            }
            
            var messages by remember { mutableStateOf(emptyList<com.flightchat.model.ChatMessage>()) }
            var users by remember { mutableStateOf(emptyList<User>()) }
            var currentScreen by remember {
                mutableStateOf(if (initialSession != null) "chat" else "start")
            }

            DisposableEffect(Unit) {
                connectionStateCallback = { connected ->
                    appState = appState.copy(isConnected = connected)
                }
                if (serverBound) {
                    serverService?.setConnectionCallback { connected ->
                        runOnUiThread {
                            connectionStateCallback?.invoke(connected)
                        }
                    }
                }
                if (clientBound) {
                    clientService?.setConnectionCallback { connected ->
                        runOnUiThread {
                            connectionStateCallback?.invoke(connected)
                        }
                    }
                }
                onDispose {
                    connectionStateCallback = null
                }
            }
            
            // 收集消息
            LaunchedEffect(Unit) {
                database.chatMessageDao().getAllMessages().collect { msgs ->
                    messages = msgs
                }
            }
            
            // 收集在线用户
            LaunchedEffect(Unit) {
                database.userDao().getOnlineUsers().collect { onlineUsers ->
                    users = onlineUsers
                }
            }
            
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.White
            ) {
                when (currentScreen) {
                    "start" -> {
                        StartScreen(
                            onHostMode = { nickname ->
                                appState = appState.copy(
                                    isHost = true,
                                    currentNickname = nickname,
                                    isConnected = false
                                )
                                saveSession(
                                    isHost = true,
                                    userId = appState.currentUserId,
                                    nickname = nickname,
                                    serverHost = ChatClientService.AUTO_SERVER_HOST,
                                    serverPort = appState.serverPort
                                )
                                requestIgnoreBatteryOptimizationsIfNeeded()
                                startServerService(appState.currentUserId, nickname, appState.serverPort)
                                currentScreen = "chat"
                            },
                            onClientMode = { nickname, hostInput ->
                                val resolvedHost = hostInput.trim().ifBlank {
                                    ChatClientService.AUTO_SERVER_HOST
                                }
                                appState = appState.copy(
                                    isHost = false,
                                    currentNickname = nickname,
                                    serverHost = resolvedHost,
                                    isConnected = false
                                )
                                saveSession(
                                    isHost = false,
                                    userId = appState.currentUserId,
                                    nickname = nickname,
                                    serverHost = resolvedHost,
                                    serverPort = appState.serverPort
                                )
                                requestIgnoreBatteryOptimizationsIfNeeded()
                                startClientService(
                                    appState.currentUserId,
                                    nickname,
                                    resolvedHost,
                                    appState.serverPort
                                )
                                currentScreen = "chat"
                            }
                        )
                    }
                    
                    "chat" -> {
                        ChatScreen(
                            messages = messages,
                            users = users,
                            onSendMessage = { content ->
                                if (appState.isHost) {
                                    serverService?.sendHostMessage(content)
                                } else {
                                    clientService?.sendMessage(content)
                                }
                            },
                            onLeaveRoom = {
                                if (appState.isHost) {
                                    stopServerService()
                                } else {
                                    stopClientService()
                                }
                                clearPersistedSession()
                                appState = appState.copy(
                                    isHost = false,
                                    isConnected = false,
                                    currentNickname = "",
                                    currentUserId = "user_${System.currentTimeMillis()}"
                                )
                                currentScreen = "start"
                            },
                            currentUserId = appState.currentUserId,
                            isConnected = appState.isConnected
                        )
                    }
                }
            }
        }
    }
    
    private fun startServerService(userId: String, nickname: String, serverPort: Int) {
        stopClientService()
        val intent = Intent(this, ChatServerService::class.java).apply {
            putExtra("userId", userId)
            putExtra("nickname", nickname)
            putExtra("serverPort", serverPort)
        }
        ContextCompat.startForegroundService(this, intent)
        if (!serverBound) {
            bindService(intent, serverConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    private fun startClientService(
        userId: String,
        nickname: String,
        serverHost: String,
        serverPort: Int
    ) {
        stopServerService()
        val intent = Intent(this, ChatClientService::class.java).apply {
            putExtra("userId", userId)
            putExtra("nickname", nickname)
            putExtra("serverHost", serverHost)
            putExtra("serverPort", serverPort)
        }
        ContextCompat.startForegroundService(this, intent)
        if (!clientBound) {
            bindService(intent, clientConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun bindRunningServerService(): Boolean {
        if (serverBound) return true
        return bindService(
            Intent(this, ChatServerService::class.java),
            serverConnection,
            0
        )
    }

    private fun bindRunningClientService(): Boolean {
        if (clientBound) return true
        return bindService(
            Intent(this, ChatClientService::class.java),
            clientConnection,
            0
        )
    }

    private fun stopServerService() {
        if (serverBound) {
            serverService?.setConnectionCallback(null)
            unbindService(serverConnection)
            serverBound = false
            serverService = null
        }
        stopService(Intent(this, ChatServerService::class.java))
    }

    private fun stopClientService() {
        if (clientBound) {
            clientService?.clearCallbacks()
            unbindService(clientConnection)
            clientBound = false
            clientService = null
        }
        stopService(Intent(this, ChatClientService::class.java))
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val permissionState = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        )
        if (permissionState != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun requestIgnoreBatteryOptimizationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return

        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (serverBound) {
            serverService?.setConnectionCallback(null)
            unbindService(serverConnection)
            serverBound = false
            serverService = null
        }
        if (clientBound) {
            clientService?.clearCallbacks()
            unbindService(clientConnection)
            clientBound = false
            clientService = null
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        return activityManager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == serviceClass.name
        }
    }

    private fun resolveRestorableSession(): PersistedSession? {
        val serverRunning = isServiceRunning(ChatServerService::class.java)
        val clientRunning = isServiceRunning(ChatClientService::class.java)
        val saved = readPersistedSession()

        return when {
            serverRunning -> saved?.takeIf { it.isHost }
                ?: PersistedSession(
                    isHost = true,
                    userId = "user_${System.currentTimeMillis()}",
                    nickname = "",
                    serverHost = ChatClientService.AUTO_SERVER_HOST,
                    serverPort = 5555
                )
            clientRunning -> saved?.takeIf { !it.isHost }
                ?: PersistedSession(
                    isHost = false,
                    userId = "user_${System.currentTimeMillis()}",
                    nickname = "",
                    serverHost = ChatClientService.AUTO_SERVER_HOST,
                    serverPort = 5555
                )
            else -> {
                clearPersistedSession()
                null
            }
        }
    }

    private fun saveSession(
        isHost: Boolean,
        userId: String,
        nickname: String,
        serverHost: String,
        serverPort: Int
    ) {
        sessionPrefs.edit()
            .putBoolean(KEY_SESSION_ACTIVE, true)
            .putBoolean(KEY_IS_HOST, isHost)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_NICKNAME, nickname)
            .putString(KEY_SERVER_HOST, serverHost)
            .putInt(KEY_SERVER_PORT, serverPort)
            .apply()
    }

    private fun readPersistedSession(): PersistedSession? {
        if (!sessionPrefs.getBoolean(KEY_SESSION_ACTIVE, false)) return null
        val isHost = sessionPrefs.getBoolean(KEY_IS_HOST, false)
        val userId = sessionPrefs.getString(KEY_USER_ID, null).orEmpty()
        val nickname = sessionPrefs.getString(KEY_NICKNAME, null).orEmpty()
        val serverHost = sessionPrefs.getString(
            KEY_SERVER_HOST,
            ChatClientService.AUTO_SERVER_HOST
        ) ?: ChatClientService.AUTO_SERVER_HOST
        val serverPort = sessionPrefs.getInt(KEY_SERVER_PORT, 5555)

        return PersistedSession(
            isHost = isHost,
            userId = userId.ifBlank { "user_${System.currentTimeMillis()}" },
            nickname = nickname,
            serverHost = serverHost,
            serverPort = serverPort
        )
    }

    private fun clearPersistedSession() {
        sessionPrefs.edit().clear().apply()
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
        private const val SESSION_PREFS_NAME = "flightchat_session"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_IS_HOST = "is_host"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_SERVER_HOST = "server_host"
        private const val KEY_SERVER_PORT = "server_port"
    }

    private data class PersistedSession(
        val isHost: Boolean,
        val userId: String,
        val nickname: String,
        val serverHost: String,
        val serverPort: Int
    )
}
