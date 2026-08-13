# 部署拓扑与环境分层

## 1. 本地开发

本地最小 profile 只启动：

- Spring Boot API/控制面；
- LiveKit（通话/互动）；
- coturn（可选但必须能切换 relay）；
- SRS（直播）；
- 数据库和现有聊天依赖。

SRS、MediaMTX、LiveKit 不应在一个默认 profile 中同时占用同一 RTMP/WebRTC 端口。每个 profile 必须有自己的配置文件、健康检查和端口表；未存在的 `srs.conf`、`livekit.yaml`、`mediamtx.yml` 不能被文档宣称可运行。

## 2. 测试环境

测试环境需要：

- HTTPS/WSS 和可验证的 Origin allowlist；
- coturn UDP 3478、TCP 3478、TLS 5349/443；
- LiveKit room/token/webhook；
- SRS WHIP/WHEP/HLS endpoint；
- 浏览器自动化节点和可控网络损伤；
- Prometheus/Grafana 或等价指标存储。

## 3. 生产环境

```text
CDN/WAF
   |
API gateway ---- Spring Boot control plane ---- DB/Redis/Kafka/outbox
   |                         |
   |                         +---- LiveKit cluster
   |                         +---- SRS edge/origin
   |                         +---- coturn pool
   |                         +---- Egress/FFmpeg workers -> object storage
   |
Web clients/OBS
```

控制面和媒体面分别扩容。LiveKit、SRS、coturn 的健康和带宽必须有独立告警；TURN egress、SFU CPU、发布/订阅数和 provider webhook 延迟纳入容量评估。

## 4. 配置和密钥

- provider URL、API key、JWT secret 通过环境或密钥管理器注入，不提交到仓库。
- token TTL 5-15 分钟；重连时重新签发，不延长永久权限。
- TURN 使用 REST 临时凭据，日志只记录 credential id，不记录 secret。
- 生产 Origin、CORS、房间 ACL 和 webhook 签名校验默认拒绝未知来源。
- LiveKit token 由其 server SDK 按 room/participant/发布订阅权限签发；coturn 使用 REST API 生成 `username=expiry:userId` 和 HMAC credential；SRS 的 WHIP/WHEP 使用部署 profile 中明确的 stream key 或 HTTP callback 鉴权，Spring 只做绑定和短期授权，不假设三者共用一种 token。

## 5. 故障策略

- LiveKit 不可用：1 对 1 可在明确 feature flag 下进入 P2P fallback；群聊不能静默退回 mesh。
- SRS ingest 故障：主播进入 `DEGRADED`，提供重连/备用 ingest；观众转 HLS fallback。
- TURN 故障：报警并切换健康 relay，不能把公共 STUN 当永久替代。
- Spring 重启：通过 provider reconciliation 恢复房间，不能批量无条件结束所有 LIVE。
