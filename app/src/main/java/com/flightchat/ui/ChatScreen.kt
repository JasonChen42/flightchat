package com.flightchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    users: List<User>,
    onSendMessage: (String) -> Unit,
    onLeaveRoom: () -> Unit,
    currentUserId: String,
    isConnected: Boolean
) {
    var messageInput by remember { mutableStateOf("") }
    val messageListState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            messageListState.animateScrollToItem(messages.lastIndex)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 顶部状态栏
        ChatTopBar(
            isConnected = isConnected,
            onlineUserCount = users.count { it.isOnline },
            onLeaveRoom = onLeaveRoom,
            modifier = Modifier.fillMaxWidth()
        )
        
        Divider()
        
        // 消息列表和在线用户
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // 消息区
            LazyColumn(
                state = messageListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                items(messages) { message ->
                    ChatMessageBubble(
                        message = message,
                        isFromCurrentUser = message.from == currentUserId,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }
            
            // 在线用户列表
            UserListPanel(
                users = users.filter { it.isOnline },
                modifier = Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFF5F5F5))
            )
        }
        
        Divider()
        
        // 消息输入区
        MessageInputBar(
            messageInput = messageInput,
            onMessageChange = { messageInput = it },
            onSendClick = {
                if (messageInput.trim().isNotEmpty()) {
                    onSendMessage(messageInput)
                    messageInput = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ChatTopBar(
    isConnected: Boolean,
    onlineUserCount: Int,
    onLeaveRoom: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            
            Text(
                text = "在线: $onlineUserCount",
                fontSize = 12.sp,
                color = Color.Gray
            )

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
                Text(
                    text = message.content,
                    color = if (isFromCurrentUser) Color.White else Color.Black,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                Text(
                    text = formatTime(message.timestamp),
                    color = if (isFromCurrentUser) Color(0xFFDDDDDD) else Color.Gray,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun UserListPanel(
    users: List<User>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .border(1.dp, Color(0xFFDDDDDD))
            .padding(8.dp)
    ) {
        Text(
            text = "在线用户",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        LazyColumn {
            items(users) { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .background(Color.White, RoundedCornerShape(4.dp))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color.Green, RoundedCornerShape(50))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = user.nickname,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun MessageInputBar(
    messageInput: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.White)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
