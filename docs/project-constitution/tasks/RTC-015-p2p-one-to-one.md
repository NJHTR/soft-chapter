# RTC-015：受控 1 对 1 P2P 实验与 SFU 回退

## 状态与边界

- 状态：`planned`
- 负责人：未认领
- 分支/开始时间：未分配
- 波次：F（规模化子任务最后执行）
- 依赖：RTC-004、RTC-007
- 默认开关：`RTC_P2P_ENABLED=false`
- 负责目录：独立 P2P provider adapter、版本化 signaling、服务端 topology/consent policy、
  TURN 临时凭据和实验指标
- 允许的共享改动：RTC 控制 API/schema、token policy、signaling 白名单，由主智能体审查
- 禁止修改：复用旧 `Call.vue` mesh；群聊、直播、Stage、录制、审核房间使用 P2P；已接通房间
  静默换房间/拓扑；在聊天 WS 转发媒体/base64 或未经限制的任意 SDP

## 目标与非目标

仅对业务 `scope=direct`（1 对 1）、恰好两人、双方针对当前 call/generation 明确同意且仍在
CONNECTING 的通话尝试 P2P。selected local/remote ICE candidate type 均为非 relay
（`host|srflx|prflx`）才使用 P2P；
任一 `relay`、超时、质量或权限问题走可审计 LiveKit SFU fallback。

非目标：P2P 不是 LiveKit 的通用替代；不保证服务器零流量；不为群聊建立 mesh；不在无真实
双浏览器/NAT 数据时开启生产开关。

## 状态机与契约

- 统一契约：`docs/contracts/rtc-scale-control-contract.md` 第 7 节。
- `DISABLED -> ELIGIBLE -> CONSENTED -> PROBING -> P2P_CONNECTED`；失败经
  `FALLING_BACK -> SFU_CONNECTED|FAILED`。
- Eligibility 同时要求：业务 `scope=direct`、两名有效成员、当前 ACCEPTED/NEGOTIATING、
  无录制/审核/Stage、feature flag 开启、ACL/token 有效。
- Consent 绑定 `call_id + topology_generation + participant_id`，双方独立同意，有 TTL，可撤销。
- Offer/answer/ICE 使用独立版本化 envelope，校验成员、seq、TTL、大小和候选数量；candidate
  可早于 remote description 缓存，重复 event_id 幂等。
- PROBING 预算 1.5～3 秒；relay、ICE failed、TURN-only 或预算到期记录稳定 reason 后回退 SFU。
- 业务已 CONNECTED 后不得静默热切换。质量/权限变化必须进入用户可见 RECONNECTING，控制面
  增加 generation/审计后才能连接原 LiveKit SFU。

## 实现输出

1. 服务端 topology eligibility、consent、generation、TTL、ACL 和审计 API/状态机。
2. 独立 P2P adapter 与版本化 offer/answer/ICE signaling，不复用旧 mesh。
3. 短期 TURN credential port、标准 `host|srflx|prflx|relay` candidate pair 分类，以及独立的
   `topology=p2p|sfu` 与 `transport_outcome=direct|srflx|relay|sfu`。
4. 有界 P2P probe、LiveKit SFU fallback 和权限/质量回退原因。
5. attempt、成功率、SFU fallback、TURN relay、RTT、loss、CPU/电量和 SFU egress 摘要。

## 验证命令

```text
mvn -f server/pom.xml -Dtest=com.douyin.rtc.p2p.** test
mvn -f server/pom.xml test
pnpm exec vue-tsc --noEmit --pretty false
pnpm exec eslint src/modules/rtc/p2p
pnpm exec vitest run src/modules/rtc/p2p
git diff --check
```

双浏览器验收逐项固定 IPv4/IPv6、UDP、TURN availability 和 candidate pair；不得仅根据
`iceConnectionState=connected` 判定 direct。

## Definition of Done

- [ ] 开关默认关闭且 fail-closed，服务端拒绝 group/live/stage/recording/moderation/未同意请求。
- [ ] 恰好两人、双方 consent、CONNECTING/generation 和权限撤销有并发/重复/乱序/过期测试。
- [ ] 独立版本化 offer/answer/ICE、短期 TURN 凭据和 1.5～3 秒预算通过契约测试。
- [ ] 只有 local/remote candidate type 均为 `host|srflx|prflx` 的 selected pair 使用 P2P；任一
  `relay`、超时和失败自动回退 LiveKit SFU。
- [ ] 已 CONNECTED 后没有静默 room/topology 切换，用户可见重连与审计路径通过。
- [ ] 分别记录 `p2p|sfu` topology、local/remote 标准 candidate type、派生的
  `direct|srflx|relay|sfu` outcome、RTT、丢包、CPU/电量、SFU egress 和稳定回退原因。
- [ ] IPv4/IPv6、直连、对称 NAT、企业网、UDP 禁用、TURN 不可用、relay、失败回退和挂断通过
  双浏览器验收，并有真实成功率/回退率/relay 比例。

## 回滚与风险

- `RTC_P2P_ENABLED=false` 停止新 attempt。PROBING 中 call 记录 `feature_disabled` 并走 SFU。
- 已 P2P CONNECTED 的会话不后台切换；异常时按 generation 进入可见 RECONNECTING/SFU fallback。
- 风险：聊天 WS 当前 call_signal 缺少完整白名单/大小/roster 校验；没有双浏览器、IPv6、NAT、
  TURN TCP/TLS、CPU/电量数据；这些关闭前不得开启灰度。

## 交付记录

- 提交哈希：未实现
- 测试结果：未执行
- 遗留风险：RTC-007 未完成，真实 P2P/NAT/TURN 环境未提供
