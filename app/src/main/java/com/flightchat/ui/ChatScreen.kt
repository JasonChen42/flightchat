package com.flightchat.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import com.flightchat.database.ChatDatabase
import com.flightchat.network.ImageStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flightchat.model.ChatMessage
import com.flightchat.model.User
import java.text.SimpleDateFormat
import java.util.*

/**
 * 聊天界面
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    users: List<User>,
    onSendMessage: (String, ChatMessage?, String) -> Unit,
    onHistoryLimit: (Int) -> Unit,
    onRetry: () -> Unit,
    onLeaveRoom: () -> Unit,
    currentUserId: String,
    isConnected: Boolean
) {
    var messageInput by remember { mutableStateOf("") }
    val messageListState = rememberLazyListState()
    val context = LocalContext.current
    val dao = remember { ChatDatabase.getInstance(context).chatMessageDao() }
    val scope = rememberCoroutineScope()
    var reply by remember { mutableStateOf<ChatMessage?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var pendingJump by remember { mutableStateOf<String?>(null) }
    var highlight by remember { mutableStateOf<String?>(null) }
    var photoOpen by remember { mutableStateOf(false) }
    var viewedImage by remember { mutableStateOf<String?>(null) }
    fun jumpTo(id: String) {
        scope.launch {
            val target = dao.find(id)
            if (target == null) Toast.makeText(context, "原消息不在本机", Toast.LENGTH_SHORT).show()
            else {
                pendingJump = id
                onHistoryLimit(dao.countSince(target.timestamp) + 30)
            }
        }
    }
    LaunchedEffect(messages, pendingJump) {
        val id = pendingJump ?: return@LaunchedEffect
        val index = messages.asReversed().indexOfFirst { it.messageId == id }
        if (index >= 0) {
            messageListState.scrollToItem(index)
            pendingJump = null
            highlight = id

        }
    }
    LaunchedEffect(highlight) {
        if (highlight != null) { delay(2000); highlight = null }
    }
    if (searchOpen) SearchMessagesDialog(dao, onDismiss = { searchOpen = false }, onSelect = {
        searchOpen = false
        jumpTo(it.messageId)
    })
    if (photoOpen) SendImageDialog(onDismiss = { photoOpen = false }, onSend = { id, caption ->
        onSendMessage(caption, reply, id)
        reply = null
        photoOpen = false
    })
    viewedImage?.let { id -> ImageViewer(id, onDismiss = { viewedImage = null }) }

    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) {
            messageListState.animateScrollToItem(0)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // 顶部状态栏
        ChatTopBar(
            isConnected = isConnected,
            onlineUsers = users.filter { it.isOnline },
            onLeaveRoom = onLeaveRoom,
            onSearch = { searchOpen = true },
            modifier = Modifier.fillMaxWidth()
        )
        
        Divider()
        
        // 消息列表
        LazyColumn(
            state = messageListState,
            // 从底部保持滚动位置，键盘改变列表高度时最新消息仍然可见。
            reverseLayout = true,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp)
        ) {
            items(messages.asReversed(), key = { it.id }) { message ->
                Column(Modifier.fillMaxWidth().background(
                    if (highlight == message.messageId) Color(0xFFFFF1B8) else Color.Transparent
                ).combinedClickable(onClick = {}, onLongClick = { reply = message })) {
                ChatMessageBubble(
                    message = message,
                    onReplyClick = { jumpTo(message.replyToId) },
                    onImageClick = { viewedImage = message.imageId },
                    isFromCurrentUser = message.from == currentUserId,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
                if (message.deliveryState == "PENDING" ||
                    (message.imageId.isNotBlank() && !ImageStore.exists(context, message.imageId))) {
                    TextButton(onClick = onRetry) { Text("重试") }
                }
                }
            }
            item {
                TextButton(onClick = { onHistoryLimit(messages.size + 100) }, modifier = Modifier.fillMaxWidth()) {
                    Text("加载更早消息")
                }
            }
        }
        if (messageListState.firstVisibleItemIndex > 3) {
            TextButton(onClick = { scope.launch { messageListState.animateScrollToItem(0) } }) { Text("回到最新消息") }
        }
        Divider()
        reply?.let { quoted ->
            Row(Modifier.fillMaxWidth().background(Color(0xFFF2F2F2)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("引用 ${quoted.nickname}：${if (quoted.type == "IMAGE") "[图片]" else quoted.content}",
                    maxLines = 2, modifier = Modifier.weight(1f), fontSize = 12.sp)
                TextButton(onClick = { reply = null }) { Text("取消") }
            }
        }
        // 消息输入区
        MessageInputBar(
            messageInput = messageInput,
            onMessageChange = { messageInput = it },
            onSendClick = {
                if (messageInput.trim().isNotEmpty()) {
                    onSendMessage(messageInput, reply, "")
                    reply = null
                    messageInput = ""
                }
            },
            onImageClick = { photoOpen = true },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ChatTopBar(
    isConnected: Boolean,
    onlineUsers: List<User>,
    onLeaveRoom: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOnlineUsers by remember { mutableStateOf(false) }

    if (showOnlineUsers) {
        OnlineUsersDialog(
            users = onlineUsers,
            onDismiss = { showOnlineUsers = false }
        )
    }

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "FlightChat",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6200EE),
            modifier = Modifier.weight(1f)
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (isConnected) Color.Green else Color.Red,
                        shape = RoundedCornerShape(50)
                    )
            )
            
            Text(
                text = if (isConnected) "已连接" else "已断开",
                fontSize = 12.sp,
                color = if (isConnected) Color.Green else Color.Red
            )
            
            TextButton(
                onClick = { showOnlineUsers = true },
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "在线: ${onlineUsers.size}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            TextButton(onClick = onSearch, contentPadding = PaddingValues(4.dp)) { Text("搜索", fontSize = 12.sp) }
            TextButton(
                onClick = onLeaveRoom,
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "退出",
                    fontSize = 13.sp,
                    color = Color(0xFFB00020)
                )
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    isFromCurrentUser: Boolean,
    onReplyClick: () -> Unit = {},
    onImageClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = message.nickname,
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
        
        Box(
            modifier = Modifier
                .background(
                    color = if (isFromCurrentUser) Color(0xFF6200EE) else Color(0xFFE8E8E8),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column {
                if (message.replyToId.isNotBlank()) {
                    Text("${message.replyNickname}：${message.replyPreview}", maxLines = 3,
                        fontSize = 12.sp, color = Color.DarkGray,
                        modifier = Modifier.background(Color(0xFFE8E3F2), RoundedCornerShape(4.dp))
                            .clickable(onClick = onReplyClick).padding(8.dp))
                    Spacer(Modifier.height(6.dp))
                }
                if (message.imageId.isNotBlank()) {
                    StoredImage(message.imageId, Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 240.dp)
                        .clickable(onClick = onImageClick))
                }
                Text(
                    text = message.content,
                    color = if (isFromCurrentUser) Color.White else Color.Black,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                Text(
                    text = formatTime(message.timestamp) + if (message.deliveryState == "PENDING") " · 等待发送 / 自动重试" else "",
                    color = if (isFromCurrentUser) Color(0xFFDDDDDD) else Color.Gray,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun OnlineUsersDialog(
    users: List<User>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "在线用户",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (users.isEmpty()) {
                Text(
                    text = "暂无在线用户",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(users) { user ->
                        OnlineUserRow(user = user)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun OnlineUserRow(user: User) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Color.Green, RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = user.nickname,
            fontSize = 14.sp,
            maxLines = 1
        )
    }
}

@Composable
fun MessageInputBar(
    messageInput: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onImageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = onImageClick, contentPadding = PaddingValues(4.dp)) { Text("图片") }
        TextField(
            value = messageInput,
            onValueChange = onMessageChange,
            placeholder = { Text("输入消息...") },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp),
            singleLine = true
        )
        
        Button(
            onClick = onSendClick,
            modifier = Modifier.heightIn(min = 56.dp)
        ) {
            Text("发送")
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
