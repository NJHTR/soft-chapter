# RTC-015：受控 1 对 1 P2P 实验与 SFU 回退

## 状态与边界

- 状态：`planned`
- 依赖：RTC-004、RTC-007
- 负责目录：独立 P2P provider adapter、版本化 signaling、TURN 临时凭据和实验指标
- 禁止修改：复用旧 `Call.vue` mesh；群聊、录制、审核房间和已接通房间拓扑热切换

## 目标

仅对 direct、双方同意且策略允许的 1 对 1 通话尝试 P2P；ICE 直连失败、质量恶化或需要 TURN 时回退 LiveKit SFU。

## Definition of Done

- [ ] 服务端策略拒绝 group、录制、审核和未同意的 P2P 请求。
- [ ] 使用独立版本化 offer/answer/ICE 信令、短期 TURN 凭据和 1.5-3 秒连接预算。
- [ ] 记录 provider attempt、候选类型、RTT、丢包、CPU/电量和回退原因。
- [ ] 直连、对称 NAT、企业网、TURN、失败回退和挂断流程通过双浏览器验收。
