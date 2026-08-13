# RTC 与直播测试计划

## 1. 契约测试

- schema：必填字段、未知字段、版本、长度、ID 类型和时间格式。
- 幂等：重复 `event_id`、重复 `client_request_id`、重复 join/leave 不增加记录和计数。
- 顺序：乱序 seq、过期 TTL、重复 answer、candidate 早到和 end-of-candidates。
- 权限：未登录、过期 token、非群成员、非主播、伪造 `to_user_ids`、错误 room role。
- 兼容：旧 `type/signal_type/data`、CSV `to_user_ids` 双读并投影到 v1；新事件不再发 CSV。

## 2. 浏览器媒体测试

- Chrome、Firefox、Safari、Android Chrome、iOS Safari 的音频/视频能力协商。
- H.264/VP8/Opus capability、设备权限拒绝、摄像头/麦克风切换、静音和屏幕共享。
- LiveKit 1 对 1、群聊 2/4/8 人；订阅、active speaker、simulcast 层切换。
- offer/answer、trickle ICE、candidate-before-PC 缓存、ICE restart、网络恢复。
- 远端视频 `srcObject`、音频播放策略、页面后台/锁屏和资源释放。

## 3. TURN/NAT 与弱网

至少覆盖：

| 场景 | RTT | 丢包 | 带宽 | 期望 |
|---|---:|---:|---:|---|
| 正常 | 20-50 ms | <1% | 8 Mbps | 720p30 稳定 |
| 移动网 | 100-200 ms | 1-5% | 1-4 Mbps | 音频稳定、视频降层 |
| 企业网 | 150-300 ms | 3-8% | 500 kbps-2 Mbps | TURN TCP/TLS、音频优先 |
| 极端 | 300-800 ms | 8-10% | 100-500 kbps | 可解释降级或结束 |

验证 STUN 直连、TURN UDP、TURN TCP、TURN TLS 443；记录 selected candidate、连接耗时、恢复率和 TURN egress。

## 4. 直播 E2E

- 主播 WHIP 成功、SRT/RTMP 兼容 ingest、主播断线重连。
- 观众 WHEP/WebRTC 首帧、LL-HLS/HLS/HTTP-FLV fallback、浏览器兼容。
- 1、100、1000 观众的 presence、点赞/聊天控制通道和 CDN 分发。
- ingest 健康状态、房间 start/end 竞态、provider 重启和房间 reconciliation。
- 禁止二进制媒体进入普通聊天 WS；legacy bridge 必须有 feature flag 和计数。

## 5. QoE 与性能

- 接通/首帧 P50/P95/P99、RTT/jitter/loss、freeze ratio、FPS、有效分辨率和码率。
- CPU/GPU/内存、SFU CPU、TURN 带宽、编码队列和 provider webhook 延迟。
- 2 小时长通话、后台恢复、反复设备切换、房间成员进出和内存泄漏。
- 录制同意、独立轨道、合屏、断点重试、音画同步、对象存储删除。

## 6. 安全与故障注入

- 信令注入、重放、超大 payload、频率放大、未授权订阅和 webhook 签名失败。
- LiveKit/SRS/TURN 任一节点不可用时的告警、降级、重试上限和人工升级。
- Spring 重启、Redis/Kafka 延迟、数据库唯一键冲突、provider webhook 重复/乱序。

## 7. 发布门禁

没有真实浏览器 + TURN + provider 的通过记录，不得把任务标记 `verified`。类型检查和构建通过只能证明静态代码可编译，不能证明清晰度、接通率或 WebRTC 可用。
