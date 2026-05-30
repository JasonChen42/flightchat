package com.flightchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 启动屏幕 - 选择主机或客户端模式
 */
@Composable
fun StartScreen(
    initialNickname: String = "",
    initialServerHost: String = "",
    canQuickEnter: Boolean = false,
    quickEnterIsHost: Boolean = false,
    onQuickEnter: () -> Unit = {},
    onHostMode: (String) -> Unit,
    onClientMode: (String, String) -> Unit
) {
    var nicknameInput by remember(initialNickname) { mutableStateOf(initialNickname) }
    var serverHostInput by remember(initialServerHost) { mutableStateOf(initialServerHost) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo/Title
        Text(
            text = "✈️ FlightChat",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6200EE),
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        Text(
            text = "飞行模式下的聊天室",
            fontSize = 16.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (canQuickEnter) {
            Button(
                onClick = onQuickEnter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32)
                )
            ) {
                Text(
                    text = if (quickEnterIsHost) "一键恢复主机" else "一键进入聊天室",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // 昵称输入
        TextField(
            value = nicknameInput,
            onValueChange = { nicknameInput = it },
            label = { Text("输入你的昵称") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        TextField(
            value = serverHostInput,
            onValueChange = { serverHostInput = it },
            label = { Text("客户端服务器IP（可留空自动发现）") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            singleLine = true
        )

        // 主机模式按钮
        Button(
            onClick = {
                if (nicknameInput.isNotBlank()) {
                    onHostMode(nicknameInput)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6200EE)
            )
        ) {
            Text(
                text = "🌐 启动热点服务器",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 客户端模式按钮
        Button(
            onClick = {
                if (nicknameInput.isNotBlank()) {
                    onClientMode(nicknameInput, serverHostInput)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF03DAC6)
            )
        ) {
            Text(
                text = "📱 自动连接服务器",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "💡 提示：同一WiFi建议填主机IP；热点模式可留空自动发现",
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
