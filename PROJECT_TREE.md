# FlightChat 项目结构树

```
flightchat/                                    # 项目根目录
├── build.gradle.kts                          # 根级 Gradle 构建配置
├── settings.gradle.kts                       # 项目设置，声明子模块
├── .gitignore                                # Git 忽略规则
├── local.properties.example                  # Android SDK 配置示例
├── init.sh                                   # 项目初始化脚本
│
├── README.md                                 # 项目总览和使用指南
├── DEVELOPMENT.md                            # 开发参考和 API 文档
├── IMPLEMENTATION_SUMMARY.md                 # 实现总结和架构说明
├── PROJECT_TREE.md                           # 本文件
│
├── .idea/                                    # IntelliJ IDEA 配置
│   └── runConfigurations.xml
│
└── app/                                      # 应用模块
    ├── build.gradle.kts                      # 应用级 Gradle 配置
    ├── proguard-rules.pro                    # ProGuard 混淆规则
    │
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml           # Android 应用清单
        │   │   ├── permissions               # 应用权限声明
        │   │   ├── activities                # Activity 声明
        │   │   └── services                  # Service 声明
        │   │
        │   ├── java/com/flightchat/          # 源代码根目录
        │   │
        │   ├── model/                        # 📦 数据模型层
        │   │   ├── ChatMessage.kt            # 聊天消息数据类
        │   │   │   ├── @Entity(table="messages")
        │   │   │   └── 字段: id, messageId, from, to, nickname, content, timestamp, type
        │   │   ├── User.kt                   # 用户信息数据类
        │   │   │   ├── @Entity(table="users")
        │   │   │   └── 字段: userId, nickname, joinedAt, isOnline, isHost
        │   │   └── AppState.kt               # 应用全局状态
        │   │       └── 字段: isHost, isConnected, currentUserId, etc.
        │   │
        │   ├── database/                     # 💾 数据库层 (Room ORM)
        │   │   ├── ChatDatabase.kt           # 数据库主类 (单例)
        │   │   │   ├── 包含 2 个表: messages, users
        │   │   │   └── 提供 getInstance() 方法
        │   │   ├── ChatMessageDao.kt         # 消息 DAO 接口
        │   │   │   ├── insert()              # 插入消息
        │   │   │   ├── getAllMessages()      # 查询全部消息 (Flow)
        │   │   │   ├── getMessagesByRecipient() # 查询特定接收者消息
        │   │   │   └── deleteAll()           # 清空消息
        │   │   └── UserDao.kt                # 用户 DAO 接口
        │   │       ├── insert()              # 插入用户
        │   │       ├── update()              # 更新用户
        │   │       ├── getOnlineUsers()      # 查询在线用户 (Flow)
        │   │       ├── getAllUsers()         # 查询所有用户
        │   │       ├── getUserById()         # 按 ID 查询
        │   │       └── deleteUser()          # 删除用户
        │   │
        │   ├── network/                      # 🌐 网络通信层
        │   │   └── MessageProtocol.kt        # 消息协议处理
        │   │       ├── encodeMessage()       # JSON 编码 (+ \n)
        │   │       ├── decodeMessage()       # JSON 解码
        │   │       ├── createUserJoinMessage()
        │   │       ├── createUserLeaveMessage()
        │   │       └── createChatMessage()
        │   │
        │   ├── server/                       # 🖥️ 服务器实现
        │   │   └── ChatServerService.kt      # 消息服务器
        │   │       ├── onStartCommand()      # 启动服务器
        │   │       ├── startServer()         # 初始化 ServerSocket
        │   │       ├── broadcastMessage()    # 广播消息给所有客户端
        │   │       ├── removeClient()        # 移除断开的客户端
        │   │       └── ClientHandler        # 内部类：处理单一客户端
        │   │           ├── run()             # 消息处理循环
        │   │           └── sendMessage()     # 发送消息
        │   │
        │   ├── client/                       # 📱 客户端实现
        │   │   └── ChatClientService.kt      # 聊天客户端
        │   │       ├── onStartCommand()      # 启动客户端
        │   │       ├── connectToServer()     # 建立连接
        │   │       ├── sendMessage()         # 发送消息
        │   │       ├── setMessageCallback()  # 注册消息回调
        │   │       ├── setConnectionCallback() # 注册连接状态回调
        │   │       └── disconnect()          # 断开连接
        │   │
        │   ├── ui/                           # 🎨 UI 层 (Jetpack Compose)
        │   │   ├── ChatScreen.kt             # 主聊天界面
        │   │   │   ├── ChatScreen()          # 主屏幕
        │   │   │   ├── ChatTopBar()          # 顶部状态栏
        │   │   │   ├── ChatMessageBubble()   # 消息气泡
        │   │   │   ├── UserListPanel()       # 用户列表面板
        │   │   │   └── MessageInputBar()     # 消息输入栏
        │   │   └── StartScreen.kt            # 启动和连接屏幕
        │   │       ├── StartScreen()         # 模式选择屏幕
        │   │       └── ClientConnectScreen() # 客户端连接屏幕
        │   │
        │   └── MainActivity.kt               # 🎯 主入口 Activity
        │       ├── onCreate()                # 初始化 UI 状态
        │       ├── startServerService()      # 启动服务器
        │       ├── startClientService()      # 启动客户端
        │       ├── 屏幕导航逻辑
        │       └── 数据库监听和 UI 更新
        │
        └── res/                              # 资源文件
            └── values/
                ├── strings.xml               # 字符串资源
                │   ├── app_name
                │   ├── mode_server/client
                │   ├── send_message
                │   └── ...
                ├── colors.xml                # 颜色定义
                │   ├── primary (#6200EE)
                │   ├── primary_dark
                │   └── accent (#03DAC6)
                └── themes.xml                # 主题定义
                    └── Theme.FlightChat
```

## 📊 文件概览

### 核心源代码文件 (14 个 .kt)
```
model/        3 files   |  数据模型层
database/     3 files   |  数据库 (Room) 层
network/      1 file    |  网络协议层
server/       1 file    |  Socket 服务器实现
client/       1 file    |  Socket 客户端实现
ui/           2 files   |  Jetpack Compose UI
root/         3 files   |  MainActivity 和工具
```

### 配置和资源文件 (12 个)
```
build/        2 files   |  Gradle 配置
manifest/     1 file    |  Android 清单
resources/    3 files   |  UI 资源 (strings, colors, themes)
proguard/     1 file    |  混淆规则
config/       2 files   |  项目级别配置
```

### 文档文件 (4 个 .md)
```
README.md                     |  项目总览和使用指南
DEVELOPMENT.md               |  开发参考和 API 文档
IMPLEMENTATION_SUMMARY.md    |  实现总结和架构说明
PROJECT_TREE.md              |  文件结构描述 (本文件)
```

## 🔄 数据流和模块交互

```
┌─────────────────────────────────┐
│    Android UI (Jetpack Compose)  │  ← StartScreen, ChatScreen
└────────────┬────────────────────┘
             │
┌────────────▼────────────────────┐
│      MainActivity (Activity)     │  ← 状态管理、屏幕导航
└────────────┬──────────┬─────────┘
             │          │
    ┌────────▼──┐  ┌────▼──────────┐
    │  Database │  │  Service      │
    │ (Room ORM)│  │  (Network)    │
    │           │  │               │
    │ • Message │  │ • Server      │
    │   DAO     │  │   Service     │
    │ • User    │  │               │
    │   DAO     │  │ • Client      │
    │           │  │   Service     │
    └────┬──────┘  └────┬──────┬───┘
         │              │      │
         │         ┌────▼──┐   │
         │         │Socket │   │
         │         │Server │   │
         │         │(Port: │   │
         │         │ 5555) │   │
         │         └───────┘   │
         │                     │
         │              ┌──────▼──────┐
         │              │Socket       │
         │              │Client       │
         │              │(Connect to  │
         │              │ 192.168.49.1:5555)
         │              └─────────────┘
         │
         └──────────────────────┬──────────────
                         Local SQLite DB
```

## 🎯 关键路径

### 主机启动流程
```
StartScreen (选择"启动热点服务器")
    ↓
MainActivity.startServerService()
    ↓
ChatServerService.onStartCommand()
    ↓
ChatServerService.startServer()
    ↓
ServerSocket 监听 5555 端口
    ↓
等待客户端连接
```

### 客户端连接流程
```
StartScreen (选择"连接到热点")
    ↓
ClientConnectScreen (输入服务器信息)
    ↓
MainActivity.startClientService()
    ↓
ChatClientService.onStartCommand()
    ↓
ChatClientService.connectToServer()
    ↓
Socket 连接到 192.168.49.1:5555
    ↓
接收服务器消息
```

### 消息发送流程
```
ChatScreen 消息输入
    ↓
用户点击"发送"
    ↓
MainActivity 调用 onSendMessage()
    ↓
创建 ChatMessage 对象
    ↓
保存到本地数据库 (ChatMessageDao)
    ↓
发送给服务 (通过回调)
    ↓
MessageProtocol.encodeMessage() (JSON 编码)
    ↓
Socket 发送
    ↓
对端 Socket 接收
    ↓
MessageProtocol.decodeMessage() (JSON 解码)
    ↓
保存到本地数据库
    ↓
UI 流监听更新
    ↓
显示在 ChatScreen
```

## 📦 依赖关系

```
MainActivity
├── 依赖: ChatScreen, StartScreen, ClientConnectScreen
├── 依赖: ChatDatabase, ChatMessageDao, UserDao
├── 依赖: ChatServerService, ChatClientService
└── 依赖: ChatMessage, User, AppState

ChatServerService
├── 依赖: MessageProtocol
└── 依赖: ChatMessage

ChatClientService
├── 依赖: MessageProtocol
└── 依赖: ChatMessage

Compose UI Components
├── 依赖: ChatMessage (数据模型)
└── 依赖: User (数据模型)

Database Layer (Room)
├── 依赖: ChatMessage, User (实体)
└── 提供给: MainActivity
```

## 🚀 加载和初始化顺序

```
1. AndroidManifest.xml 声明权限和 Service
2. MainActivity 启动
   ├── 初始化 AppState
   ├── 初始化数据库引用
   ├── 收集数据库查询流
   └── 显示 StartScreen
3. 用户选择模式
   ├── 主机: startServerService()
   │   └── ChatServerService 启动
   │       └── ServerSocket 监听
   └── 客户端: startClientService()
       └── ChatClientService 启动
           └── Socket 连接
4. 进入 ChatScreen
   ├── 显示聊天界面
   ├── 监听消息更新
   └── 处理用户输入
5. App 运行
   ├── 接收/发送消息
   ├── 数据库存储
   └── UI 实时更新
6. 用户退出或销毁
   ├── onDestroy() 调用
   ├── stopService()
   └── 资源清理
```

## 💡 扩展点

### 添加新功能的位置

1. **新消息类型**
   - 在 `ChatMessage.kt` 中扩展 `type` 字段
   - 在 `MessageProtocol.kt` 中添加处理方法
   - 在相应的 Service 中处理新类型

2. **新的 UI 元素**
   - 在 `ui/` 目录中创建新的 Compose 函数
   - 在 `MainActivity` 中集成

3. **数据库表**
   - 在 `model/` 中定义新实体
   - 在 `database/` 中创建新 DAO
   - 在 `ChatDatabase` 中注册

4. **网络功能**
   - 在 `MessageProtocol` 中扩展协议
   - 在 Service 中实现业务逻辑
   - 添加相应的 UI 反馈

---

**总结**: 这个项目遵循标准的 Android 分层架构，清晰的模块划分使得代码易于维护和扩展。
