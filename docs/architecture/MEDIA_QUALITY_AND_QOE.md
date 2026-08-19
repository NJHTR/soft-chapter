# 媒体质量与 QoE 设计

## 1. 质量原则

清晰度由有效采集分辨率、编码分辨率、帧率、码率、关键帧间隔、冻结率和端到端延迟共同决定。采集请求不能固定为 4K/60；先读取设备 `getCapabilities()`，再按设备、网络和 UI 视图协商。

## 2. 编解码基线

| 媒体 | 基线 | 可选增强 | 备注 |
|---|---|---|---|
| 通话视频 | H.264 或 VP8 | VP9/AV1 | 必须等待浏览器 capability；H.265 不作 WebRTC 基线 |
| 通话音频 | Opus 48 kHz | RED/FEC | 默认 mono 24-64 kbps，音频优先 |
| 直播视频 | H.264/AV1 依 provider | H.265/AV1 | 兼容出口统一提供 H.264 |
| 直播音频 | AAC 或 Opus | - | 依据 WHEP/HLS/HTTP-FLV 出口选择 |

## 3. 通话档位

| 场景 | 分辨率 | 帧率 | 目标码率 |
|---|---:|---:|---:|
| 1 对 1 默认 | 1280x720 | 30 | 1.2-2.5 Mbps |
| 1 对 1 高质量 | 1920x1080 | 30 | 2.5-4.5 Mbps |
| 群聊 tile | 640x360 | 30 | 400-700 kbps |
| 群聊低层 | 320x180 | 15/30 | 150-300 kbps |
| 屏幕共享 | 1280x720 | 5-15 | 依据 detail/content hint |

SFU 启用 simulcast/dynacast，客户端只订阅可见或 active speaker 的适当层。发送器参数变更必须使用 `RTCRtpSender.setParameters()` 或 provider API，不能重建页面状态。

客户端减载不是客户端 relay：浏览器可以编码多层、暂停未消费层、暂停隐藏视频并保留音频，
但群聊媒体仍由 LiveKit SFU 转发。`adaptiveStream` 只有在 RemoteTrack 的真实 attach/detach
和元素可见性契约完成后才能开启；当前 `MediaStream/srcObject` 适配器暂不直接打开该选项。
容量估算按房间参与者数和订阅关系计算，不能把客户端 CPU/上行转发当作服务器的免费替代。

## 4. 最小指标

客户端每 2-5 秒采样并聚合：

```text
capture_width, capture_height, capture_fps
encode_fps, frames_encoded, frames_dropped
out_bitrate, available_outgoing_bitrate
rtt_ms, jitter_ms, packets_lost, packets_sent
pli_count, fir_count, nack_count
ice_state, connection_state, selected_candidate_type
startup_ms, connected_ms, first_frame_ms
freeze_ratio, rendered_fps, rendered_width, rendered_height
glass_to_glass_ms, cpu_percent, gpu_percent, memory_mb
```

服务端按 room、provider、设备、浏览器、网络类型和版本聚合 P50/P95/P99，不把单次峰值当质量。

## 4.1 指标测量定义

当前 RTC adapter 已提供 provider-neutral `RtcQoeSnapshot`：接通后每 3 秒读取远端
LiveKit inbound-rtp 统计并保留最近一次快照。采集只做观测，不直接改变编码层或把原始
stats 写入 Kafka/聊天 WS；后续 RTC-007/011 负责采样聚合、服务端上报和降层门禁。

| SLO | 测量方法 | cohort 与分母 | 失败处置 |
|---|---|---|---|
| 接通 P95 < 3 秒 | `call.created` 到 provider `connected` 的单调时钟差 | 每个成功/失败 CallSession，按浏览器和网络分组；P95 分母为该 cohort 的全部尝试 | 超阈值冻结升档，检查 token、TURN、SFU 和 provider webhook |
| 首帧 P95 < 2 秒 | 首个有效远端 `framesRendered > 0` 减 join 时间 | 每个视频订阅轨道；音频-only 不计入分母 | 降低默认层，检查关键帧和订阅策略 |
| RTT P95 < 250 ms | `remote-inbound-rtp`/`candidate-pair` 每 2 秒采样，按会话取 P95 | 连接存活样本；剔除无有效 candidate 的样本并单独告警 | 强制降层或切 TURN 诊断 |
| 视频冻结率 < 2% | `freeze_duration / active_video_duration`，按 5 秒窗口聚合 | 有效视频订阅轨道；按活跃视频时长加权 | 降帧/降层并请求 PLI |
| TURN 成功率 >= 99% | `relay connected / relay attempts` | 按 UDP、TCP、TLS 443 和地域分别统计 | 切换健康 relay，阻断无凭据的公共 STUN fallback |

所有 SLO 至少需要 100 个有效会话或 24 小时窗口后才用于发布判断；低样本只做诊断，不伪造通过。

## 5. 弱网策略

1. 可用码率下降或丢包升高：先降 simulcast 层和视频码率。
2. 视频持续冻结：降低分辨率/帧率，触发关键帧请求。
3. 带宽低于音视频最低阈值：关闭视频，维持 Opus 音频。
4. ICE disconnected：10-15 秒内执行 ICE restart/rejoin，指数退避且复用 call_id。
5. 网络恢复后按滞后阈值逐级升档，防止频繁抖动。

## 6. 验收基线

- 通话接通 P95 < 3 秒，首帧 P95 < 2 秒。
- 正常网络 720p30 实际发送码率 >= 1.2 Mbps。
- WebRTC RTT P95 < 250 ms，音频丢包 < 3%，视频冻结率 < 2%。
- TURN（UDP/TCP/TLS 443）连接成功率 >= 99%。
- 断线后 10 秒内恢复率 >= 95%；恢复失败必须明确降级或结束原因。
- 直播互动 WebRTC glass-to-glass P95 < 1 秒；LL-HLS P95 < 5 秒。

## 7. 当前 WebCodecs 迁移约束

legacy 代码只记录这些已知缺陷，不再追加修复任务；`EncodedVideoChunk.copyTo()`、`getVideoTracks()[0]`、`isConfigSupported()` 的 `await`、decoder.configure、长度校验和 bitmap 生命周期都属于退役前风险清单。生产主路径直接迁移至 WebRTC RTP/WHEP，legacy 关闭日期和阈值见开发路线图。
