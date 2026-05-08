# FlightChat - 飞行模式聊天应用

一个创新的 Android 应用，让两部手机在飞行模式下通过 WiFi 热点进行聊天通信。

## 📋 功能特性

- ✈️ **飞行模式兼容** - 在飞行模式下通过 WiFi 热点运行
- 🌐 **双模式支持** - 支持服务器模式（主机）和客户端模式
- 💬 **实时聊天** - 基于 Socket 的低延迟消息传输
- 👥 **用户管理** - 在线用户列表和到线/离线通知
- 📱 **现代 UI** - 使用 Jetpack Compose 构建
- 💾 **消息持久化** - Room 数据库存储聊天历史
- 🔗 **局域网通信** - 基于 TCP/IP 的可靠传输

## 🏗️ 项目架构

```
flightchat/
├── app/
│   ├── src/main/java/com/flightchat/
│   │   ├── model/              # 数据模型
│   │   │   ├── ChatMessage.kt
│   │   │   ├── User.kt
│   │   │   └── AppState.kt
│   │   ├── database/           # 数据库层 (Room)
│   │   │   ├── ChatDatabase.kt
│   │   │   ├── ChatMessageDao.kt
│   │   │   └── UserDao.kt
│   │   ├── network/            # 网络协议
│   │   │   └── MessageProtocol.kt
│   │   ├── server/             # 服务器实现
│   │   │   └── ChatServerService.kt
│   │   ├── client/             # 客户端实现
│   │   │   └── ChatClientService.kt
│   │   ├── ui/                 # UI 组件
│   │   │   ├── ChatScreen.kt
│   │   │   └── StartScreen.kt
│   │   └── MainActivity.kt      # 主入口
│   ├── res/
│   │   └── values/
│   │       ├── strings.xml
│   │       ├── colors.xml
│   │       └── themes.xml
│   ├── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── .gitignore
```

## 🔧 技术栈

| 组件 | 技术 | 说明 |
|------|------|------|
| 网络通信 | Socket + TCP | 基于 WiFi 热点局域网 |
| 服务器 | Java NIO + 线程池 | 多客户端并发处理 |
| UI 框架 | Jetpack Compose | 现代响应式 UI |
| 数据存储 | Room ORM + SQLite | 本地消息持久化 |
| 异步编程 | Coroutines | 协程异步处理 |
| 序列化 | Gson | JSON 消息格式 |

## 📱 使用流程

### 主机端（服务器）
1. 启动应用，选择"启动热点服务器"
2. 输入昵称后确认
3. 应用启动 Socket 服务器（监听端口 5555）
4. 在系统设置中打开手机 WiFi 热点，SSID 可自行设置
5. 等待其他手机连接

### 客户端
1. 先在系统设置中连接到主机手机的 WiFi 热点 `FlightChat`
2. 打开应用，输入昵称，选择"自动连接服务器"
3. 应用会自动识别热点网关并连接到端口 5555

## 🔌 消息协议

所有消息使用 JSON 格式，通过 TCP 传输（行分隔）：

```json
{
  "messageId": "唯一标识符",
  "type": "MESSAGE|USER_JOIN|USER_LEAVE",
  "from": "发送者ID",
  "to": "all|specific_user_id",
  "nickname": "发送者昵称",
  "content": "消息内容",
  "timestamp": 1712973600000
}
```

## 🚀 开发指南

### 环境要求
- Android Studio Arctic Fox 或更高版本
- Android SDK 21+ (最小)
- Android SDK 34 (目标)
- Kotlin 1.9.10+
- Gradle 8.1.0+

### 构建项目
```bash
./gradlew assembleDebug
```

### 安装应用
```bash
./gradlew installDebug
```

### 运行应用
```bash
./gradlew runDebug
```

## 📝 核心模块说明

### 1. 消息协议 (MessageProtocol.kt)
- 提供消息编码/解码功能
- 支持多种消息类型（MESSAGE, USER_JOIN, USER_LEAVE）

### 2. 服务器 (ChatServerService.kt)
- 监听客户端连接
- 管理用户列表
- 广播消息给所有客户端
- 处理用户上线/离线事件

### 3. 客户端 (ChatClientService.kt)
- 连接到服务器
- 发送和接收消息
- 回调接口用于 UI 更新

### 4. 数据库 (Room DAOs)
- ChatMessageDao: 消息历史查询
- UserDao: 用户状态管理

### 5. UI (Compose)
- StartScreen: 模式选择和昵称输入
- ClientConnectScreen: 服务器连接配置
- ChatScreen: 主聊天界面
- 实时消息显示和用户列表

## ⚙️ 权限申请

应用需要以下权限：
- INTERNET - 网络通信
- ACCESS_NETWORK_STATE - 网络状态检测
- CHANGE_NETWORK_STATE - WiFi 状态变更
- CHANGE_WIFI_STATE - WiFi 状态兼容（现代 Android 通常仍需用户手动开启热点）
- ACCESS_WIFI_STATE - WiFi 信息访问
- ACCESS_FINE_LOCATION - 定位（某些系统要求）

## 🐛 故障排除

### 客户端连接失败
- 确保服务器已启动 WiFi 热点
- 验证客户端已连接到正确的 WiFi 网络
- 查看 Logcat 中客户端尝试的自动探测地址
- 检查防火墙设置，确保端口 5555 开放

### 消息无法发送/接收
- 检查网络连接状态
- 查看 Logcat 中的错误日志
- 确保双方应用版本一致

### 应用崩溃
- 检查 Android 版本兼容性
- 清除应用数据和缓存后重试
- 查看 Logcat 获取详细错误信息

## 🔐 安全考虑

- ⚠️ 当前版本仅在本地网络（热点）中通信，不支持互联网通信
- ⚠️ 消息以明文传输，仅适用于本地非敏感场景
- ⚠️ 不建议在生产环境使用，仅用于学习和演示

## 📄 许可证

MIT License - 自由使用和修改

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📞 联系方式

如有问题，欢迎反馈。
# flightchat
