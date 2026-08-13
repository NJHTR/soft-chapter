# RTC-002：媒体 provider bootstrap 环境

## 状态与边界

- 状态：`planned`
- 依赖：RTC-001
- 负责目录：`deploy/rtc/`、`deploy/streaming/`、`docker-compose.streaming.yml` 及本任务测试文档
- 禁止修改：`src/`、`server/` 业务控制面、`Call.vue`、WebCodecs 和 native engine

## 目标

建立可重复启动的 LiveKit、coturn、SRS provider 环境，补齐实际存在的配置文件、端口、健康检查和 provider CLI smoke test。该任务不依赖 Spring 业务 token API。

## 关键契约

- LiveKit：HTTP/WS、UDP 媒体端口、签名 webhook。
- coturn：3478 UDP/TCP、5349/443 TLS、REST 临时凭据。
- SRS：WHIP/WHEP、SRT/RTMP 兼容端口、HTTP callback 或 stream key。
- 每个 profile 只启用一套直播媒体服务，不能端口冲突。

## 验证与 DoD

- [ ] compose 引用的每个配置文件真实存在且通过配置检查。
- [ ] LiveKit、coturn、SRS 健康检查和端口检查通过。
- [ ] provider CLI/最小浏览器测试能完成真实媒体路径，不使用 SDP echo。
- [ ] 记录 TURN UDP/TCP/TLS relay 结果和失败原因。
- [ ] 密钥来自环境注入，日志无 token/credential。
- [ ] 失败可停止、清理和重新启动，回滚只切换 profile/镜像版本。

