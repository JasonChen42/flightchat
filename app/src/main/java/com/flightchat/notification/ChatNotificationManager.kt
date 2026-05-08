package com.flightchat.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.flightchat.MainActivity
import com.flightchat.R
import com.flightchat.model.ChatMessage
import kotlin.math.absoluteValue

object ChatNotificationManager {
    private const val CHANNEL_ID = "flightchat_messages"
    private const val CHANNEL_NAME = "聊天消息"
    private const val SERVICE_CHANNEL_ID = "flightchat_service"
    private const val SERVICE_CHANNEL_NAME = "聊天室保活"

    fun showMessage(context: Context, message: ChatMessage) {
        if (message.type != "MESSAGE" || message.content.isBlank()) return
        if (!hasNotificationPermission(context)) return

        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(message.nickname.ifBlank { "FlightChat" })
            .setContentText(message.content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createMainPendingIntent(context, 0))
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(message), notification)
    }

    fun showUserJoined(context: Context, message: ChatMessage) {
        if (message.type != "USER_JOIN" && message.type != "USER_PRESENT") return
        if (!hasNotificationPermission(context)) return

        ensureChannel(context)

        val nickname = message.nickname.ifBlank { "有人" }
        val content = when (message.type) {
            "USER_PRESENT" -> "$nickname 已在聊天室"
            else -> "$nickname 加入了聊天室"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                if (message.type == "USER_PRESENT") "已有用户在线" else "用户进入聊天室"
            )
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createMainPendingIntent(context, notificationId(message)))
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(message), notification)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val messageChannel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "FlightChat 收到的新聊天消息"
        }
        val serviceChannel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            SERVICE_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持聊天室在后台持续运行"
            setShowBadge(false)
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(messageChannel)
        notificationManager.createNotificationChannel(serviceChannel)
    }

    fun startForegroundSession(
        service: Service,
        notificationId: Int,
        title: String,
        text: String
    ) {
        ensureChannel(service)
        val intent = Intent(service, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            service,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(service, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        ServiceCompat.startForeground(
            service,
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createMainPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notificationId(message: ChatMessage): Int {
        val key = message.messageId.ifBlank { "${message.from}_${message.timestamp}" }
        return key.hashCode().absoluteValue
    }
}
