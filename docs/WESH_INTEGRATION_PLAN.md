# FlightChat Wesh 集成方案

> 记录日期：2026-05-30
>
> 背景：FlightChat 当前是一个 Android 原生项目，目标是在飞行模式/热点局域网下聊天。本文评估如何把 Berty/Wesh Network Protocol 的离线优先、P2P、端到端加密能力结合进当前项目。

## 1. 结论

Wesh 和 FlightChat 的产品方向高度匹配，但它不适合作为一个小型网络库直接塞进当前 TCP 代码里。Wesh 更像是一个完整通信内核，覆盖身份、设备、群组、消息日志、离线同步、加密、P2P 发现和复制。

推荐路线：

1. 先抽象 FlightChat 的聊天传输层，保留现有 TCP 热点模式。
2. 再做 Wesh 原型验证，确认 Android 构建、两机互通、消息收发、后台生命周期都可控。
3. 原型成功后新增 `WeshTransport`，作为实验模式接入现有 UI。
4. 最后再决定是否把产品从“主机/客户端热点聊天室”升级成“邀请制 P2P 离线聊天室”。

不建议现在直接全量替换。原因是 Wesh 协议和 API 仍有演进风险，Android 侧需要 Go/gomobile/libp2p/IPFS 相关构建链路，工程成本和调试复杂度明显高于当前项目规模。

## 2. 资料摘要

主要参考：

- Wesh protocol docs: https://berty.tech/docs/protocol
- Wesh Network Toolkit: https://github.com/berty/weshnet
- Go package docs: https://pkg.go.dev/berty.tech/weshnet/v2
- Berty app repository: https://github.com/berty/berty

关键点：

- Wesh 之前叫 Berty Protocol，现在属于 Wesh Network。
- 协议目标是安全通信，支持同账号多设备、联系人一对一聊天、多成员群组。
- 网络底层基于 IPFS/libp2p，可以通过 DHT、rendezvous point、直接传输发现和同步 peer。
- 离线场景下可用 Android Nearby、iOS Multipeer Connectivity、BLE 等直连传输。
- 消息使用 CRDT/OrbitDB 风格的日志结构，靠 Lamport Clock 等机制处理离线分叉后的最终一致排序。
- 通信端到端加密，使用 X25519、Ed25519、HKDF、symmetric ratchet 等机制。
- Wesh 工具包主要是 Go 实现，对外通过 gRPC API 暴露能力，也可以通过 gomobile 桥接到移动端。
- 官方文档明确提示：协议实现仍在进行中，未彻底审计，部分功能可能未实现或未来会变化。

## 3. 当前 FlightChat 架构

当前项目核心结构：

- `app/src/main/java/com/flightchat/server/ChatServerService.kt`
  - 手机作为主机，启动 TCP server。
  - 监听 `0.0.0.0:<port>`。
  - 管理在线用户和客户端 socket。
  - 收到消息后写入 Room，并广播给其他客户端。

- `app/src/main/java/com/flightchat/client/ChatClientService.kt`
  - 手机作为客户端，连接主机。
  - 支持自动探测网关、缓存主机、扫描子网。
  - 连接后发送 `USER_JOIN`。
  - 接收服务端广播消息，写入 Room。

- `app/src/main/java/com/flightchat/network/MessageProtocol.kt`
  - 用 Gson 编解码 `ChatMessage`。
  - TCP 行分隔 JSON。
  - 当前消息是明文。

- `app/src/main/java/com/flightchat/model/ChatMessage.kt`
  - Room entity。
  - 字段包括 `messageId`、`from`、`to`、`nickname`、`content`、`timestamp`、`type`。

- `app/src/main/java/com/flightchat/ui/ChatScreen.kt`
  - UI 直接展示 Room 流中的消息和用户。

当前架构特点：

- 简单、直接、低延迟。
- 依赖一台主机在线。
- 不支持主机离线后的多 peer 继续聊天。
- 不支持消息端到端加密。
- 消息排序依赖设备时间戳。
- 没有真正的离线合并语义。
- 网络模型是星型，而不是 P2P mesh。

## 4. Wesh 能补齐什么

### 4.1 去中心化

当前 FlightChat 需要一个主机。主机退出后，聊天室基本失效。

Wesh 的群组模型可以让每台设备都是 peer。任何在线 peer 都能和其他 peer 同步消息。产品形态可以从“启动热点服务器/连接服务器”变成“创建聊天室/加入聊天室”。

### 4.2 离线优先

当前 TCP 模式要求所有客户端能连到主机。两个客户端之间不能脱离主机继续聊天。

Wesh 使用日志同步和 CRDT 思路。设备 A、B 离线期间产生的消息，在之后重新遇到其他 peer 时可以合并。这个能力对飞行模式、户外、弱网、临时热点很有价值。

### 4.3 端到端加密

当前消息是 JSON 明文。任何同网段抓包者都能看到内容。

Wesh 在协议层处理身份、握手、群组秘密、消息 ratchet。接入 Wesh 后，FlightChat 不需要自己设计加密协议。

### 4.4 群组邀请

当前客户端需要知道主机 IP 或依赖自动发现。

Wesh 的 group invitation 可以变成：

- 创建聊天室后展示二维码。
- 对方扫码加入。
- 或分享一个本地链接/文本邀请。

这比让用户填 IP 更自然。

### 4.5 多设备和复制

Wesh 有 account group、linked device、replication device/server 的设计。

对 FlightChat 来说，短期不必实现这些高级能力，但长期可以支持：

- 同一用户多设备同步。
- 一个低权限复制节点提供消息可用性。
- 无法解密消息的中继/缓存节点。

## 5. 推荐目标架构

### 5.1 保持 UI 和本地存储稳定

不要先动 UI，也不要一开始改 Room 数据模型太多。当前 UI 的 `messages` 和 `users` 流是可复用的。

目标是把网络层收敛到统一接口：

```kotlin
interface ChatTransport {
    val connectionState: Flow<ConnectionState>
    val incomingMessages: Flow<ChatMessage>
    val onlineUsers: Flow<List<User>>

    suspend fun start(config: ChatTransportConfig)
    suspend fun send(content: String, to: String = "all")
    suspend fun stop()
}
```

然后拆成两个实现：

```text
ChatTransport
├── TcpHotspotTransport  // 当前 server/client TCP 模式
└── WeshTransport        // 后续实验模式
```

### 5.2 服务层重构方向

当前有两个 service：

- `ChatServerService`
- `ChatClientService`

后续可以演进成：

```text
ChatForegroundService
└── ChatTransportManager
    ├── TcpHotspotTransport
    └── WeshTransport
```

短期不必马上合并两个 service。第一阶段可以只抽接口，内部仍然使用原来的 server/client 代码。

### 5.3 数据模型演进

当前 `ChatMessage` 建议补充这些字段，先可 nullable，不影响旧数据：

```kotlin
val transport: String = "tcp"          // tcp / wesh
val roomId: String = "default"         // 当前聊天室或 Wesh group id
val senderDeviceId: String = ""        // 后续 Wesh device/member device 映射
val logicalClock: Long? = null         // 为非时间戳排序预留
val remoteMessageId: String? = null    // Wesh 侧消息/operation id
```

注意：这一步可以晚一点做。先做传输抽象时，可以继续沿用当前 `ChatMessage`。

### 5.4 排序策略

当前 UI 按 `timestamp` 排序，这在中心服务器模型里够用，但在离线合并场景不可靠。

Wesh 模式下建议：

1. 展示排序优先使用 Wesh/CRDT 返回的稳定顺序。
2. 没有稳定顺序时，用 `logicalClock + senderId`。
3. `timestamp` 只作为显示时间，不作为冲突裁决依据。

## 6. 分阶段实施计划

### Phase 0：确认基线

目标：确保当前 TCP 版本可构建、可安装、两机可聊天。

任务：

- 运行 `./gradlew test`。
- 运行 `./gradlew assembleDebug`。
- 两台手机验证：
  - 主机启动热点服务器。
  - 客户端自动发现或手动 IP 连接。
  - 双向发送消息。
  - 后台保活。
  - 退出后在线状态更新。

产出：

- 当前版本行为记录。
- 明确后续改动是否破坏现有能力。

### Phase 1：抽象当前传输层

目标：把“怎么发消息”和 UI/Room/通知解耦。

任务：

- 新增 `network/transport/ChatTransport.kt`。
- 定义 `ChatTransportConfig`、`ConnectionState`。
- 将现有 server/client 的公共行为整理出来。
- 让 `MainActivity` 不直接知道太多 `ChatServerService` / `ChatClientService` 细节。
- 保持 UI 行为不变。

建议做法：

- 不要一口气重写 server/client。
- 先包一层适配器，减少风险。
- 测试仍应覆盖 `MessageProtocol` 编解码和基础发送流程。

验收：

- 现有 TCP 热点聊天功能保持不变。
- `MainActivity` 对网络模式的判断明显减少。
- 后续新增 `WeshTransport` 不需要重写 `ChatScreen`。

### Phase 2：Wesh Android 构建原型

目标：证明 Wesh 能在当前 Android 项目旁边构建和运行。

任务：

- 新建独立实验目录，例如 `experiments/wesh-bridge/`。
- 编写最小 Go bridge，封装 Wesh service。
- 使用 gomobile 生成 Android AAR。
- 把 AAR 接进 app 的 debug 构建，先不接 UI。
- 在 Android service 中启动/停止 Wesh 节点。

原型 API 可以非常小：

```go
type WeshBridge struct {}

func NewWeshBridge(storagePath string) *WeshBridge
func (b *WeshBridge) Start() error
func (b *WeshBridge) Stop() error
func (b *WeshBridge) CreateGroup() (string, error)
func (b *WeshBridge) JoinGroup(invite string) error
func (b *WeshBridge) SendText(groupID string, text string) error
func (b *WeshBridge) SubscribeMessages(callback MessageCallback) error
```

注意：

- Android 私有目录用于 Wesh 持久化身份和数据。
- 前台 service 负责生命周期。
- 先只支持同一 WiFi/热点下两机通信。
- 暂时不要同时追求 BLE、Nearby、复制节点、多设备账号。

验收：

- AAR 能稳定构建。
- App 能启动 Wesh 节点。
- 两台设备能创建/加入同一 group。
- 能发送一条文本并在另一台设备收到。
- 退出 app 或 service 后资源能释放。

### Phase 3：接入实验模式

目标：让用户能在 UI 中选择 Wesh 模式聊天。

任务：

- 新增 `WeshTransport`。
- StartScreen 增加入口：
  - `热点 TCP 模式`
  - `P2P Wesh 实验模式`
- Wesh 模式下提供：
  - 创建聊天室。
  - 显示邀请文本/二维码。
  - 加入聊天室。
  - 发送文本。
  - 接收文本。
- Wesh 收到的 payload 转换为现有 `ChatMessage` 写入 Room。

映射关系：

```text
Wesh account/device        -> FlightChat userId/deviceId
Wesh group                 -> FlightChat roomId
Wesh group invitation      -> FlightChat join code / QR code
Wesh app message payload   -> ChatMessage JSON 或 protobuf payload
Wesh event stream          -> incomingMessages Flow
```

验收：

- TCP 模式仍可用。
- Wesh 模式可以两机互发文本。
- UI 不需要知道消息来自 TCP 还是 Wesh。
- 断网/重连后能继续同步基础消息。

### Phase 4：消息模型和历史同步升级

目标：让 Wesh 的离线同步能力真正体现在 FlightChat 里。

任务：

- 为 `messages.messageId` 增加唯一索引或去重逻辑。
- 保存 Wesh operation/message id。
- 保存 room/group id。
- 调整 DAO 查询：
  - 按 room 查询。
  - 使用稳定排序字段。
  - 避免重复插入。
- 增加“同步中/已同步/离线可用”等状态。

验收：

- 同一条 Wesh 消息重复收到不会重复展示。
- 设备 A/B 离线期间分别发送消息，之后重连能合并展示。
- 展示顺序稳定，不依赖本机时间。

### Phase 5：产品形态升级

目标：从“热点主机聊天室”演进成“P2P 离线聊天室”。

可选改动：

- 去掉或弱化“主机/客户端”概念。
- 首页改成：
  - 创建聊天室
  - 加入聊天室
  - 最近聊天室
- 邀请方式：
  - 二维码。
  - 文本码。
  - Android share intent。
- 在线用户列表改为：
  - 已知成员。
  - 最近同步时间。
  - 当前可达 peer。

注意：

Wesh 的“群组成员”和当前“在线用户”不是完全同一概念。P2P 离线系统里，用户可能是群成员但当前不可达。UI 要避免把“离线”误解成“已退出群组”。

## 7. 需要特别注意的风险

### 7.1 协议成熟度

Wesh 文档明确提示实现仍在进行中，协议未彻底审计。Berty README 也提示 API 可能变化。

应对：

- 先作为实验模式，不立刻替换 TCP。
- Wesh 相关代码隔离在 `WeshTransport` 和 bridge 模块。
- 不承诺生产级安全。

### 7.2 Android 构建复杂度

Wesh 是 Go 生态，Android 需要 gomobile/AAR。可能遇到：

- Go 版本要求。
- Android NDK 要求。
- gomobile bind 兼容问题。
- AAR 包体变大。
- CI 构建变慢。

应对：

- 先独立实验目录验证。
- AAR 作为 debug-only 或本地产物接入。
- 等稳定后再自动化构建。

### 7.3 后台运行和电量

P2P 节点比普通 TCP socket 重。移动端后台限制会影响同步。

应对：

- 继续使用 foreground service。
- 明确请求电池优化豁免。
- Wesh 节点只在聊天会话活跃时运行。
- UI 显示同步状态，不假装一直在线。

### 7.4 用户体验变化

当前用户理解的是“一个人开热点服务器，另一个人连接”。Wesh 更自然的是“创建/加入群组”。

应对：

- 初期保留两种模式。
- Wesh 模式标注为实验。
- 用二维码替代 IP 输入。

### 7.5 群成员移除和邀请失效

Wesh 文档提到异步系统里群邀请没有传统过期语义，成员移除也不是简单问题。

应对：

- 第一版不做踢人。
- 需要移除成员时，创建新群。
- 邀请码提示“拥有邀请即可加入，请谨慎分享”。

## 8. 不建议走的路线

### 8.1 自己在 TCP 上重造加密和 CRDT

看起来比接 Wesh 简单，但长期会变成自己维护协议、安全、同步、冲突合并。这个方向风险更高。

除非项目目标只是教学演示，否则不建议。

### 8.2 直接删除 TCP，全量切 Wesh

这会一次性引入太多变量：

- 构建链路。
- Android 生命周期。
- Wesh API。
- 消息模型。
- UI 产品形态。
- 两机真机调试。

不符合当前项目稳步推进的需求。

### 8.3 只把 Wesh 当“加密库”

Wesh 的价值在完整协议栈。如果只想加密当前 TCP 消息，应该选择成熟的应用层加密方案，而不是引入整套 Wesh。

## 9. 最小可行原型范围

第一版 Wesh 原型只做这些：

- 单聊天室。
- 两台 Android 手机。
- 同一 WiFi/热点网络。
- 文本消息。
- 二维码或文本邀请。
- 持久化账号和 group。
- 前台 service 启停。

明确不做：

- 文件附件。
- 多聊天室管理。
- BLE/Nearby 优化。
- 多设备账号同步。
- 复制服务器。
- 踢人/权限管理。
- 复杂在线状态。

这样能最快验证核心假设：Wesh 是否能可靠承载 FlightChat 的基础聊天。

## 10. 验收清单

### TCP 基线

- [ ] 主机能启动服务。
- [ ] 客户端能自动发现或手动连接。
- [ ] 双向文本消息可达。
- [ ] Room 历史正常展示。
- [ ] 后台保活不退。

### 传输抽象

- [ ] UI 不直接依赖 TCP 细节。
- [ ] TCP 模式行为不变。
- [ ] 新增 transport 不需要重写 `ChatScreen`。

### Wesh 原型

- [ ] Android AAR 可构建。
- [ ] App 可启动 Wesh 节点。
- [ ] 可创建 group。
- [ ] 可加入 group。
- [ ] 两机可互发文本。
- [ ] 断开/重连后不崩溃。

### Wesh 实验模式

- [ ] StartScreen 可选择 Wesh 模式。
- [ ] 可展示邀请。
- [ ] 可输入/扫码加入。
- [ ] 消息写入 Room。
- [ ] 消息不重复展示。
- [ ] TCP 模式仍可使用。

## 11. 推荐下一步

如果要继续推进，最建议先做 Phase 1：

1. 新增 `ChatTransport` 抽象。
2. 把现有 TCP server/client 包成 `TcpHotspotTransport` 或等价适配层。
3. 保证 UI 和现有功能不变。

完成这一步后，Wesh 原型可以独立推进，不会把主应用搞乱。

