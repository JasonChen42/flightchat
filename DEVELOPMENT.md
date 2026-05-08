# FlightChat - 快速开发指南

## 📂 核心文件导览

### 1️⃣ 数据模型 (model/)

#### ChatMessage.kt
- **用途**: 聊天消息实体
- **关键字段**: messageId, from, to, nickname, content, timestamp, type
- **类型**: USER_JOIN | USER_LEAVE | MESSAGE
- **使用**: 在所有网络通信和数据库操作中

#### User.kt  
- **用途**: 用户信息实体
- **关键字段**: userId, nickname, joinedAt, isOnline, isHost
- **使用**: 管理在线/离线用户状态

#### AppState.kt
- **用途**: 应用全局状态
- **用途**: 存储当前连接状态、用户信息、服务器配置

---

### 2️⃣ 数据库 (database/)

#### ChatDatabase.kt (单例)
```kotlin
// 获取实例
val db = ChatDatabase.getInstance(context)
val messageDao = db.chatMessageDao()
val userDao = db.userDao()
```

#### ChatMessageDao.kt
```kotlin
// 查询所有消息
getAllMessages(): Flow<List<ChatMessage>>

// 查询特定收件人消息
getMessagesByRecipient(to: String): Flow<List<ChatMessage>>

// 插入消息
insert(message: ChatMessage)
```

#### UserDao.kt
```kotlin
// 获取在线用户
getOnlineUsers(): Flow<List<User>>

// 插入/更新用户
insert(user: User)
update(user: User)
```

---

### 3️⃣ 网络通信 (network/)

#### MessageProtocol.kt

**编码消息**
```kotlin
val message = ChatMessage(from = "user1", content = "Hello")
val json = MessageProtocol.encodeMessage(message) // 返回单行 JSON，Socket 写入时追加换行分隔
```

**解码消息**
```kotlin
val json = """{"from":"user1","content":"Hello"}"""
val message = MessageProtocol.decodeMessage(json)
```

**创建特殊消息**
```kotlin
// 用户加入
MessageProtocol.createUserJoinMessage(userId, nickname)

// 用户离开
MessageProtocol.createUserLeaveMessage(userId, nickname)

// 聊天消息
MessageProtocol.createChatMessage(userId, nickname, content, to)
```

---

### 4️⃣ 服务器实现 (server/)

#### ChatServerService.kt

**启动**
```kotlin
val intent = Intent(context, ChatServerService::class.java)
startService(intent)
```

**工作流程**
1. 绑定端口 5555
2. 监听客户端连接
3. 为每个客户端创建 ClientHandler
4. 接收消息并广播给其他客户端
5. 处理用户上线/离线事件

**关键方法**
```kotlin
// 广播消息给所有客户端
broadcastMessage(message: ChatMessage, fromClientId: String? = null)

// 移除断开的客户端
removeClient(clientId: String)
```

---

### 5️⃣ 客户端实现 (client/)

#### ChatClientService.kt

**启动**
```kotlin
val intent = Intent(context, ChatClientService::class.java).apply {
    putExtra("userId", "user123")
    putExtra("nickname", "张三")
    putExtra("serverHost", "192.168.49.1")
    putExtra("serverPort", 5555)
}
startService(intent)
```

**发送消息**
```kotlin
clientService.sendMessage("Hello, World!")
```

**事件回调**
```kotlin
// 接收消息
clientService.setMessageCallback { message ->
    updateUI(message)
}

// 连接状态
clientService.setConnectionCallback { isConnected ->
    updateConnectionStatus(isConnected)
}
```

---

### 6️⃣ UI 组件 (ui/)

#### StartScreen.kt
- 应用启动界面
- 昵称输入
- 模式选择（主机/客户端）

#### ClientConnectScreen.kt
- 服务器地址和端口输入
- 连接到服务器

#### ChatScreen.kt
- 消息显示列表
- 在线用户列表
- 消息输入框

---

### 7️⃣ 主 Activity

#### MainActivity.kt
- 管理屏幕导航
- 初始化服务
- 响应用户操作
- 与数据库交互

---

## 🔄 数据流向

```
用户输入 (UI)
    ↓
MainActivity 处理事件
    ↓
创建 ChatMessage 模型
    ↓
ClassicMessageDao 存储消息
    ↓
发送给 Service (ClientService/ServerService)
    ↓
MessageProtocol 编码
    ↓
Socket 发送
    ↓
接收端 Socket 读取
    ↓
MessageProtocol 解码
    ↓
更新 UI 和数据库
```

---

## 🌐 网络流

### 主机流程
1. 启动 ChatServerService → 监听端口 5555
2. 客户端连接 → 创建 ClientHandler
3. 接收消息 → 广播给其他客户端
4. 存储到本地数据库

### 客户端流程
1. 启动 ChatClientService → 连接到服务器
2. 发送用户信息
3. 监听服务器消息
4. 更新 UI 和本地数据库

---

## 🔧 常见操作

### 添加用户到在线列表
```kotlin
scope.launch {
    database.userDao().insert(
        User(
            userId = "user123",
            nickname = "张三",
            isOnline = true,
            isHost = false
        )
    )
}
```

### 保存消息到数据库
```kotlin
scope.launch {
    database.chatMessageDao().insert(message)
}
```

### 监听消息变化
```kotlin
LaunchedEffect(Unit) {
    database.chatMessageDao().getAllMessages().collect { messages ->
        // 更新 UI
    }
}
```

### 监听在线用户
```kotlin
LaunchedEffect(Unit) {
    database.userDao().getOnlineUsers().collect { users ->
        // 更新用户列表 UI
    }
}
```

---

## 📋 消息类型和格式

### 用户加入
```json
{
  "type": "USER_JOIN",
  "from": "user123",
  "to": "all",
  "nickname": "张三",
  "content": "张三 加入了聊天室",
  "timestamp": 1712973600000
}
```

### 用户离开
```json
{
  "type": "USER_LEAVE",
  "from": "user123",
  "to": "all",
  "nickname": "张三",
  "content": "张三 离开了聊天室",
  "timestamp": 1712973600000
}
```

### 聊天消息
```json
{
  "type": "MESSAGE",
  "from": "user123",
  "to": "all",
  "nickname": "张三",
  "content": "大家好",
  "timestamp": 1712973600000
}
```

---

## 🚀 扩展功能建议

1. **消息加密** - 添加 AES 加密
2. **私聊功能** - 修改 to 字段实现单对单消息
3. **文件传输** - 扩展协议支持文件
4. **消息搜索** - 在 DAO 中添加模糊查询
5. **用户头像** - 添加头像 URL 字段
6. **表情和富文本** - 支持 Markdown 或富文本格式
7. **消息已读状态** - 添加已读标记
8. **语音聊天** - 集成音频库
9. **视频通话** - 使用 WebRTC 或 Jitsi
10. **消息撤回** - 支持消息编辑和撤回

---

## 🐛 调试技巧

### 查看网络日志
```bash
adb logcat | grep ChatServerService
adb logcat | grep ChatClientService
```

### 检查数据库
```kotlin
// 在 Database 类中添加
Room.databaseBuilder(context, ChatDatabase::class.java, "flightchat_db")
    .allowMainThreadQueries() // 仅用于调试!
    .build()
```

### 模拟器 WiFi 热点
使用 Android Emulator 的虚拟 WiFi，或使用两个模拟器实例进行测试。

---

## 📚 相关文档

- [Android Room 文档](https://developer.android.com/training/data-storage/room)
- [Jetpack Compose 文档](https://developer.android.com/jetpack/compose)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [Socket 编程](https://docs.oracle.com/javase/tutorial/networking/sockets/)
