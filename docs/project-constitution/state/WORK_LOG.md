# 工作日志

## 2026-08-13：RTC-001 文档基线落地

- 独立智能体完成只读评估（架构、WebRTC、契约、宪法审查），结论一致：当前主要问题是媒体链路与职责边界未成立，而非码率参数。
- 审查修正已合入工作区：宪法 v1.1（§2.4 LiveKit 原生信令与应用控制事件边界、§3 新增 `rtc-persistence` 统一 call ledger 所有权、§7 治理/版本/弃用与 legacy 退役里程碑）、RTC-002~010 任务文件、状态文件与开发计划更新。
- 提交 `85262e2 docs(RTC-001): 实时媒体宪法与架构基线`（34 文件，纯治理/架构文档；`.ai`、`.ai-company` 项目记录一并入库）。
- 修复了会话间遗留的 `admin/design.md` index 空文件问题：用户的 staged 内容（99 行）已恢复，不在本次提交内。
- 用户业务改动（直播、streaming-engine、WebSocket、后台、`docs/runtime/`、`docs/verification/`）原样保留，未纳入提交。
- RTC-001 DoD 全部满足，标记 `completed`；下一任务 RTC-002（provider bootstrap）。

## 2026-08-13：RTC-001 基线勘察

### 工作区快照

- 分支：`dev/full`
- HEAD：`f361a12 feat: 模型训练`
- 跟踪分支：`origin/dev/full`
- 工作区：脏，存在用户已有直播、WebSocket、streaming-engine、后台和文档改动。
- 处理决定：不回滚、不覆盖、不把这些改动误归属于 RTC-001；本轮新增文件限定在设计与治理文档。

### 已执行检查

```text
git status --short
git branch --show-current
git log --oneline --decorate -20
git log --stat -10
git branch -a
git tag --sort=-creatordate
rg --files -g '!node_modules' -g '!dist'
pnpm exec vue-tsc --noEmit --pretty false   # 只读审查记录为通过
pnpm run build-only                         # 只读审查记录为通过
```

后端 Maven、浏览器 WebRTC E2E、TURN/NAT 矩阵和已部署媒体服务本轮未验证，不能宣称通过。

### 现状结论

1. 直播当前是 WebCodecs/自定义 WebSocket 与文本控制混用，前后端二进制链路不闭合；发送器还有 `copyTo()`、`getVideoTracks()` 和空 canvas 问题。
2. WebRTC offer 接口是 SDP echo，C++ WebRTCStreamer 是占位；这不是码率调参问题。
3. 1 对 1/群聊使用浏览器 mesh，只有公共 STUN，没有 TURN、SFU、候选缓存、完整重连和稳定的远端视频绑定。
4. Spring Boot、聊天 WS、数据库和 native engine 的职责边界尚未固化；实时通话记录也缺少独立 call ledger 和幂等事件。
5. Docker compose 声明了多套媒体服务，但配置文件和真实部署验证不完整，不能把 compose 文件当作已运行基础设施。

### 决策

- 通话与互动连麦采用 LiveKit SFU；coturn 作为生产穿透边缘。
- 直播采用 SRS ingest/distribution，WebRTC/WHEP 作为低延迟出口，HLS/HTTP-FLV 作为兼容和规模 fallback。
- Spring Boot 只做控制面、token、权限、业务事件和 webhook；媒体字节不经过业务 WebSocket。
- H.264/VP8 + Opus 为浏览器基线；AV1 是可选增强，H.265 不作为 WebRTC 基线。
- 现有 `/ws/live`、Call.vue mesh、WebCodecs WebSocket 和 C++ WebRTC stub 进入迁移清单，禁止新增生产能力。

### 下一步

完成 RTC-001 文档提交后，按依赖顺序认领 RTC-002：补齐可重复的 LiveKit/coturn/SRS 开发环境、健康检查和最小 token/ingest 验证。
