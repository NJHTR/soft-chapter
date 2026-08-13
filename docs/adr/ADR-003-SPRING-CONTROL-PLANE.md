# ADR-003：Spring Boot 只做控制面

- 状态：accepted
- 日期：2026-08-13

Spring Boot 管理用户身份、房间、成员、权限、短期 token、生命周期命令、信令路由、webhook、审计和业务投影。它不解析或回显 SDP，不转发 RTP/视频帧，不把媒体放入聊天 WebSocket。

现有 `/api/live/engine/webrtc/offer` 的 SDP echo 必须在真实 provider 代理或 token API 替代后下线；兼容期最多返回明确的 `provider_not_ready`，不能返回伪造 answer。
