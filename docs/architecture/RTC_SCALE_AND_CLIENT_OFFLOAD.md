# RTC 容量与客户端减载设计

## 1. 先区分压力

| 压力面 | 主要指标 | 扩展方式 |
|---|---|---|
| 控制面 | API/WS QPS、DB/Redis、token、webhook 延迟 | Spring 无状态多实例、Redis、outbox/Kafka；不承载媒体帧 |
| SFU 媒体面 | ingress/egress Mbps、pps、CPU、内存、房间、参与者、发布/订阅轨道 | LiveKit 多节点、Redis 路由、room placement、admission |
| TURN 边缘 | relay allocations、relay bytes、UDP/TCP/TLS 成功率 | coturn 区域池、扩大 relay 端口、健康切换 |
| 直播观看面 | SRS 出口、WHEP/HLS/HTTP-FLV、CDN 命中率 | SRS edge/origin + CDN，不把观众放进互动 SFU |

当前本地 compose 只有一个 LiveKit 节点和有限 UDP/TURN 端口池，属于开发配置，不能按生产万人规模估算。

## 2. 推荐拓扑

```text
                         +----------------------+
                         | Spring control plane |
                         | auth / ACL / token   |
                         | call ledger / QoE    |
                         +----+------------+----+
                              |            |
                    Redis room routing   Kafka QoE summary
                              |            |
        +---------------------+------------+-------------------+
        |                         |                            |
   LiveKit SFU A             LiveKit SFU B                 coturn pool
   1:1 / group / stage       1:1 / group / stage            UDP/TCP/TLS
        |                         |
        +----------- WebRTC clients --------------------------+

   Stage publisher -> SRS -> WHEP/LL-HLS/HLS/HTTP-FLV -> CDN -> audience
```

## 3. 客户端减载策略

### 3.1 1 对 1

默认仍使用 LiveKit，保证权限、重连、录制和可观测性一致。后续可以增加 `p2p-fallback`：

- 只允许 `scope=direct` 且恰好两人；
- 只在 `CONNECTING` 阶段选择，双方同意后才尝试；
- 直连 ICE 成功才使用 P2P，超时、TURN relay 或质量恶化时回到 LiveKit；
- 已经 `CONNECTED` 后不得静默换房间或改变录制/审计语义；
- 必须使用独立版本化信令、临时 TURN 凭据、隐私提示和灰度开关。

P2P 不能保证服务器零流量：NAT 失败时 TURN 仍然中继，控制面也始终存在。

### 3.2 群聊和连麦

- 单客户端只发布一条摄像头上行，LiveKit 负责转发。
- 摄像头使用 simulcast 低/中/高层，dynacast 根据订阅者暂停不用的层。
- 只有可见 tile、主画面和 active speaker 订阅高层；隐藏、最小化和后台页面优先暂停视频，音频保留。
- 参与者超过 4 人时默认 audio-first，视频订阅只给主画面和可见 tile；超过 8/16 人必须进入 stage + audience 或 breakout room。

当前兼容性基线先对多人房间使用 LOW 视频层、active speaker 使用 MEDIUM，避免改动现有
`MediaStream` 绑定；RTC-012 完成 publication/可见性契约后，再把主画面和 active speaker
提升到 HIGH，并对屏外轨道执行 `setSubscribed(false)`。

当前 adapter 仍把 `RemoteTrack` 聚合为 `MediaStream` 并交给 `<video srcObject>`，所以必须先完成 publication 与元素的 attach/detach 契约，再启用 `adaptiveStream`；不能仅改一个布尔值。

### 3.3 直播和万人观看

“万人通话”必须拆成业务上的少量互动者和大量单向观众：

- 主播/嘉宾：LiveKit stage，首期不超过 8 人，后续 16 人需压测；
- 普通观众：SRS 的 WHEP 或 LL-HLS/HLS/HTTP-FLV，经 CDN 分发；
- 观众申请上麦时才进入受控互动房间，或进入独立 breakout room；
- 不允许让一万名观众都发布视频或互相建立 PeerConnection。

## 4. 容量预算与 admission

每个节点至少按以下维度做 admission：CPU、内存、出站 Mbps、RTP pps、连接数、已发布/已订阅轨道、TURN relay bytes。任何一个维度达到 80% 时停止新房间，70% 连续 5 分钟告警并扩容，预留至少 30% headroom。Prometheus 使用低基数标签（region/provider/codec/version/node）；room/user/call 级 QoE 聚合写入 OTel、ClickHouse/Timescale 或 Kafka 摘要。

压测矩阵：

| 矩阵 | 目标 |
|---|---|
| 100 × 2 人房间 | 1 对 1 并发、混合音频/视频 |
| 100 × 8 人房间 | 群聊全订阅与选择性订阅对比 |
| 1000 × 2 人房间 | 多节点房间放置和连接上限 |
| 1 stage + 10k audience | SRS/CDN 长尾分发，不创建 10k LiveKit participant |

## 5. 任务拆分

| 任务 | 内容 | 依赖 |
|---|---|---|
| RTC-011 | SFU/TURN/客户端 QoE 基线、容量公式、admission、压测 harness | RTC-007 |
| RTC-012 | publication 生命周期、可见订阅、simulcast/dynacast/adaptiveStream、音频优先 | RTC-005、RTC-007 |
| RTC-013 | Redis 共享路由、LiveKit 多节点、room placement、TURN 区域池 | RTC-011 |
| RTC-014 | stage + audience、SRS/CDN、上麦和 breakout room | RTC-006、RTC-013 |
| RTC-015 | 受控 1 对 1 P2P 实验和 SFU 回退 | RTC-004、RTC-007 |
