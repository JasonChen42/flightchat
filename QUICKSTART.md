# ⚡ FlightChat 快速开始指南

## 📥 5 分钟快速开始

### 第一步：打开项目

```bash
# 进入项目目录
cd /home/jason/development/flightchat

# 用 Android Studio 打开
# Android Studio → Open → 选择此文件夹
```

### 第二步：等待 Gradle 同步

- Android Studio 会自动下载依赖
- 等待"Build" 面板提示完成
- 大约需要 1-3 分钟

### 第三步：编译

```
菜单 → Build → Build Bundle(s) / APK(s) → Build APK(s)
或者快捷键: Ctrl+F9
```

### 第四步：部署到设备

```
菜单 → Run → Run 'app'
或快捷键: Shift+F10
```

选择你的设备或启动模拟器

### 第五步：测试

#### 主机设备（设备 1）
1. 打开应用
2. 选择 "🌐 启动热点服务器"
3. 输入昵称，点击按钮
4. 在手机设置中开启 WiFi 热点

#### 客户端设备（设备 2）
1. 先在系统设置中连接到设备 1 的 WiFi 热点（SSID: FlightChat）
2. 打开应用
3. 输入昵称，选择 "📱 自动连接服务器"
4. 应用会自动识别热点网关并连接到端口 5555

#### 验证
- 两个设备上都能看到对方发送的消息
- 在线用户列表实时更新
- 消息显示发送者昵称和时间

---

## 🎯 常见命令

### Gradle 命令

```bash
# 清理构建缓存
./gradlew clean

# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本
./gradlew assembleRelease

# 在连接的设备上运行
./gradlew runDebug

# 运行单元测试
./gradlew test

# 查看项目依赖
./gradlew dependencies
```

### Android Studio 快捷键

| 快捷键 | 功能 |
|---------|------|
| Ctrl+F9 | 编译项目 |
| Shift+F10 | 运行应用 |
| Ctrl+Shift+F10 | 重新运行 |
| Ctrl+Alt+L | 格式化代码 |
| Ctrl+/ | 注释/取消注释 |
| Shift+Ctrl+/ | 块注释 |
| Alt+Enter | 显示代码建议 |

---

## 📱 测试设置

### 使用两个 Android 模拟器

```bash
# 启动模拟器 1
emulator -avd Pixel_4_API_34 &

# 启动模拟器 2
emulator -avd Pixel_5_API_34 &

# 查看连接的设备
adb devices
```

### 使用真机

1. **启用开发者模式**
   - 设置 → 关于手机 → 连续点击版本号 7 次

2. **启用 USB 调试**
   - 设置 → 开发者选项 → USB 调试

3. **连接 USB**
   - 用 USB 线连接电脑
   - 在设备上允许"USB 调试"

4. **验证连接**
   ```bash
   adb devices
   ```

---

## 🔧 DEBUG 调试

### 查看应用日志

```bash
# 查看所有日志
adb logcat

# 过滤特定应用日志
adb logcat | grep "FlightChat"

# 过滤特定 Tag
adb logcat | grep "ChatServerService"
adb logcat | grep "ChatClientService"

# 清空日志缓冲
adb logcat -c
```

### 在 Android Studio 中调试

1. **添加断点**
   - 在代码行号区域点击鼠标左键

2. **运行调试**
   - 菜单 → Run → Debug 'app'
   - 或快捷键: Shift+F9

3. **调试窗口**
   - Step Over (F10) - 单步执行
   - Step Into (F11) - 进入函数
   - Step Out (Shift+F11) - 跳出函数
   - Continue (F9) - 继续执行

---

## ❌ 常见问题

### 1. 无法连接到服务器

**症状**：客户端显示"连接失败"

**排查**：
```bash
# 检查两个设备是否在同一网络；目标地址以 Logcat 里的 Trying server 为准
ping <自动探测到的热点网关 IP>

# 检查防火墙
adb shell iptables -L

# 查看 logcat 错误
adb logcat | grep "Connection\|Error\|Exception"
```

**解决**：
- 确保主机已开启 WiFi 热点
- 确保服务器已启动（日志显示"Server started on port 5555"）
- 检查客户端是否已经连接到主机热点
- 查看日志里 `Trying server ...:5555` 的自动探测地址

### 2. 消息无法发送/接收

**症状**：应用连接了但消息没有出现

**排查**：
```bash
# 检查网络连通性
ping <另一个设备的 IP>

# 查看详细日志
adb logcat | grep "ChatServerService\|ChatClientService\|message"
```

**解决**：
- 检查两个设备的网络连接
- 在接收端查看是否有监听消息的回调
- 检查数据库是否保存了消息

### 3. 应用崩溃

**症状**：应用立即关闭或报 ANR

**排查**：
```bash
# 查看崩溃日志
adb logcat | grep "FATAL\|Exception\|ANR"

# 查看完整堆栈跟踪
adb logcat | grep -A 10 "AndroidRuntime"
```

**解决**：
- 检查权限是否已授予
- 确保目标设备 API 级别 ≥ 21
- 查看 Android Studio 的 Logcat 面板获取详细错误

### 4. Gradle 同步失败

**症状**：无法下载依赖，编译失败

**排查**：
```bash
# 查看详细错误信息
./gradlew sync --info
```

**解决**：
- 检查网络连接
- 尝试更新 gradle: `./gradlew wrapper --gradle-version 8.1.0`
- 清除缓存: `rm -rf ~/.gradle/caches`

---

## 🎨 修改应用

### 修改颜色主题

编辑 `app/src/main/res/values/colors.xml`:
```xml
<color name="primary">#6200EE</color>           <!-- 主色调 -->
<color name="primary_dark">#3700B3</color>     <!-- 深色 -->
<color name="accent">#03DAC6</color>           <!-- 强调色 -->
```

### 修改字符串资源

编辑 `app/src/main/res/values/strings.xml`:
```xml
<string name="app_name">FlightChat</string>
<string name="mode_server">启动热点服务器</string>
<!-- 更多... -->
```

### 修改 UI 布局

编辑 `app/src/main/java/com/flightchat/ui/ChatScreen.kt`:
```kotlin
@Composable
fun ChatScreen(...) {
    // 在这里修改 UI
}
```

---

## 📦 生成发布版本

### 生成签名 APK

1. **创建密钥库**
   ```bash
   keytool -genkey -v -keystore flightchat.jks -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **编辑 build.gradle.kts**
   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file("path/to/flightchat.jks")
           storePassword = "password"
           keyAlias = "alias"
           keyPassword = "password"
       }
   }
   ```

3. **构建 Release APK**
   ```bash
   ./gradlew assembleRelease
   ```

APK 位置: `app/build/outputs/apk/release/app-release.apk`

---

## 📚 进一步阅读

| 文档 | 内容 |
|------|------|
| [README.md](README.md) | 项目总览、使用指南、故障排除 |
| [DEVELOPMENT.md](DEVELOPMENT.md) | 开发参考、API 文档、常见操作 |
| [PROJECT_TREE.md](PROJECT_TREE.md) | 项目结构、文件组织、数据流 |
| [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) | 实现总结、架构设计、技术栈 |
| [CHECKLIST.md](CHECKLIST.md) | 完整检查清单、项目状态 |

---

## 🆘 获取帮助

### 查看日志

```bash
# 获取完整系统日志
adb logcat > system.log

# 实时查看特定组件日志
adb logcat | grep "ChatServerService"
```

### 检查项目结构

```bash
# 列出所有源文件
find app/src/main/java -name "*.kt" -type f

# 列出所有资源
find app/src/main/res -type f
```

### 运行诊断

```bash
# 检查 Android 版本
adb shell getprop ro.build.version.sdk

# 检查网络连接
adb shell ping -c 5 8.8.8.8

# 检查端口
adb shell netstat | grep 5555
```

---

## ✅ 完成清单

在开始之前，确保你已经：
- [ ] 安装了 Android Studio
- [ ] 下载了 Android SDK (API 34)
- [ ] 连接了一个设备或启动了模拟器
- [ ] 有稳定的网络连接
- [ ] 已阅读本快速开始指南

---

**祝你使用愉快！🚀**

如有问题，请参考 [README.md](README.md) 中的故障排除部分。
