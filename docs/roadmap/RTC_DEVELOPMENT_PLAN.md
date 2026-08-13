# 实时媒体开发计划

## 阶段 0：治理和止血（RTC-001）

交付宪法、状态机、事件 schema、ADR、QoE 指标和测试计划。立即冻结“加大码率/继续 base64 WS/继续 SDP echo”类改动。

## 阶段 1：媒体基础设施（RTC-002）

目标：可重复启动 LiveKit、coturn、SRS；补齐配置文件、端口、健康检查和 provider CLI smoke test，不依赖 Spring 业务 token API。同步修复 native unavailable 的安全回退，避免 JNI 缺失阻断服务。

## 阶段 1B：控制面（RTC-003）

目标：在 provider bootstrap 可用的同时，新增 `rtc_call_session`、participant、event ledger；实现创建/接受/拒绝/结束、TTL、幂等、ACL、LiveKit token 和 webhook。控制面可以用 provider CLI/测试密钥做契约测试，不把 provider smoke 反向耦合到未完成的业务 API。

## 阶段 2：1 对 1（RTC-004）

目标：接入真实 LiveKit token 和 1 对 1 客户端。保留旧聊天消息作为投影，Call.vue 只做兼容壳。

## 阶段 3：群聊和连麦（RTC-005）

目标：迁移到 SFU，首期 8 人；实现 roster、发布/订阅、simulcast、active speaker、屏幕共享、成员进出和重连。禁止把群聊降级为无限 mesh。

## 阶段 4：直播媒体面（RTC-006）

目标：主播 WHIP，兼容 SRT/RTMP；观众 WHEP/WebRTC，LL-HLS/HLS/HTTP-FLV fallback；聊天/点赞仍为控制通道；presence 单一权威；按房间 feature flag 灰度迁移。

## 阶段 5：质量和可运营性（RTC-007/008）

目标：统计 QoE、动态码率/分层、弱网恢复、录制同意、Egress/DVR、异步转码、对象存储、审计、告警和管理端质量视图。

## 阶段 6：收口和规模（RTC-009/010）

目标：退役旧媒体 WS、mesh、WebRTC echo 和未经验证的 C++ stub；完成跨浏览器、TURN/NAT、弱网、长通话、负载、故障注入和回滚演练，再扩展多地域与 CDN。

## Legacy 退出里程碑

| 日期 | 门槛 | 动作 | 回滚 |
|---|---|---|---|
| 2026-08-31 | 旧路径不再新增功能 | 冻结 `/ws/live` 媒体、Call.vue mesh 和 SDP echo 的生产 feature | 保留现有 flag |
| 2026-09-30 | provider 灰度完成，真实会话 >= 100，主要浏览器通过 | 新用户默认 LiveKit/SRS，legacy 仅白名单 | 按房间/版本恢复 flag |
| 2026-10-31 | legacy 使用率 < 1%，错误率不高于 provider 2 倍 | 关闭生产默认入口 | 24 小时内恢复入口 |
| 2026-11-30 | 观测窗口、数据迁移和审计完成 | 删除 legacy 代码和未链接 C++ WebRTC stub | 只能回滚到已发布 provider 版本 |

指标未达标时必须新建风险记录和批准后的日期，不能直接跳过门槛。

## 每阶段必须回答的问题

1. 真实媒体路径是什么，谁拥有它？
2. 控制面是否仍在转发媒体或回显 SDP？
3. 状态、事件和记录是否幂等且可回放？
4. 正常网、弱网、TURN、断线和 provider 故障的指标是什么？
5. 如何灰度、回滚和退役旧路径？
