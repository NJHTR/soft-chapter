# RTC 扩容与客户端分摊续开发提示词

将以下内容复制到 Codex 任务中，继续推进本仓库的音视频扩容和客户端减载工作。

```text
你是本仓库的主开发智能体，继续负责 Douyin 项目的 RTC、直播和实时媒体扩容工作。

## 当前背景

项目当前使用 LiveKit SFU 处理音视频通话，Spring Boot 负责鉴权、房间、信令和状态，不直接转发媒体。

已经完成：

- 1 对 1 和多人音视频通话基础能力
- LiveKit dynacast/simulcast 配置
- 基础客户端媒体控制
- RTC 架构、容量和客户端减载文档
- 项目宪法和任务文档
- 已有提交：`34a35cf`

当前重要设计结论：

1. 1 对 1 通话可以实验性支持 WebRTC P2P，但必须有 SFU/TURN 回退。
2. 3～8 人群聊继续使用 SFU，禁止默认使用全连接 Mesh。
3. 万人场景不能让所有用户进入同一个双向 WebRTC 房间，必须采用：
   - 少量嘉宾进入 LiveKit Stage
   - 普通观众通过 SRS/CDN/HLS/LL-HLS/WHEP 观看
   - 互动观众按需申请上麦
4. 不能贸然开启 adaptiveStream，因为当前前端仍然将 LiveKit 轨道聚合成 MediaStream，必须先解决轨道绑定和可见性同步问题。
5. 不能把所有媒体流交给服务器转码，也不能让客户端无控制地互相转发。

## 开始前必须读取

```text
docs/project-constitution/PROJECT_CONSTITUTION.md
docs/project-constitution/TASK_PROTOCOL.md
docs/project-constitution/AGENTGIT_PROTOCOL.md
docs/project-constitution/state/PROJECT_STATE.yaml
docs/project-constitution/state/WORK_LOG.md
docs/project-constitution/tasks/TASK_INDEX.md
docs/adr/ADR-005-CLIENT-OFFLOAD-AND-SCALE.md
docs/architecture/RTC_SCALE_AND_CLIENT_OFFLOAD.md
docs/architecture/MEDIA_QUALITY_AND_QOE.md
docs/contracts/live-media-contract.md
docs/contracts/rtc-control.openapi.yaml
src/modules/rtc/adapter/livekitAdapter.ts
src/modules/rtc/store/useRtcStore.ts
src/modules/rtc/components/CallPanel.vue
src/modules/rtc/types.ts
deploy/streaming/
```

同时检查：

```text
git status --short
git branch --show-current
git log --oneline --decorate -20
git diff
git diff --cached
rg --files src/modules/rtc server/src/main/java/com/douyin/rtc docs
```

不得覆盖、回滚或清理用户已有的未提交修改。

## 本轮目标

继续实现“客户端减载 + RTC 横向扩容”，优先完成基础设施和可验证的低风险能力，不做临时补丁。

### RTC-012：选择性订阅和客户端媒体策略

实现稳定的远端订阅策略：

- 1 对 1：主画面高质量，小窗中/低质量，音频始终优先。
- 3～8 人群聊：可见 tile 为 MEDIUM，非可见 tile 为 LOW 或暂停视频，当前发言人自动提升质量。
- 屏幕共享优先级高于摄像头。
- 视频关闭后只保留头像和音频。
- 必须真正控制 LiveKit Track subscription，不能只隐藏 DOM。
- 确保摄像头关闭/重新打开后，远端状态同步且不产生黑屏、重复绑定或 MediaStream 泄漏。
- adaptiveStream 只有在轨道绑定逻辑完成并有测试后才能开启。

建议实现目录：

```text
src/modules/rtc/adapter/livekitAdapter.ts
src/modules/rtc/adapter/rtcMediaPort.ts
src/modules/rtc/store/useRtcStore.ts
src/modules/rtc/components/CallPanel.vue
src/modules/rtc/types.ts
```

需要新增或完善：

- `setRemoteVideoQuality(peerId, quality)`
- `setParticipantSubscription(peerId, options)`
- `setVisibleParticipants(peerIds)`
- `setActiveSpeaker(peerId)`
- 统一的 participant/track 状态事件
- 订阅策略单元测试

### RTC-013：LiveKit 多节点扩容

设计并实现可部署的多节点基础：

- LiveKit 节点无状态化。
- Redis 用于房间调度、节点发现和分布式状态。
- 房间按稳定 hash 或容量进行分片。
- TURN 服务池化。
- 不允许把媒体流复制进 Kafka。
- Spring Boot 只负责控制面和审计。
- 增加房间数、发布者数、订阅者数、出站带宽、CPU、内存、UDP 丢包率、RTT、重连率、NACK/PLI/FIR、首帧时间和卡顿率指标。

如果仓库没有完整生产部署代码，先实现配置模板、接口契约、监控指标和文档，不要伪造已经完成的集群能力。

### RTC-014：Stage + Audience 直播架构

采用以下媒体路径：

```text
互动嘉宾：浏览器 → LiveKit Stage/SFU
普通观众：LiveKit Stage → SRS/转码/Origin → CDN → HLS/LL-HLS/WHEP
```

要求：

- 互动嘉宾数量有明确上限。
- 普通观众不进入全量 WebRTC 房间。
- 观众申请上麦经过后端权限和房间状态机。
- 上麦和下麦必须幂等。
- 主播端显示当前嘉宾和上麦状态。
- 直播间支持降级为纯 CDN 观看。
- 记录 WebRTC、WHEP、HLS 三种播放模式的 QoE 指标。

### RTC-015：受控 1 对 1 P2P 实验

只对 1 对 1 通话提供 feature flag：

```text
RTC_P2P_ENABLED=false
```

要求：

- 默认关闭或灰度开启。
- P2P 通过 ICE 完成连通性检测。
- 直连失败自动回退 LiveKit SFU。
- TURN 中继不能被误判为 P2P 直连。
- P2P 不适用于群聊、直播、多人房间。
- 记录连接类型：`direct`、`srflx`、`relay`、`sfu`。
- 增加 NAT、IPv6、禁用 UDP、TURN 不可用等故障注入测试。

## 架构原则

1. WebRTC 负责实时媒体，Kafka 不传输音视频帧。
2. Spring Boot 不转发媒体字节。
3. 1 对 1 可以 P2P，但必须有 SFU/TURN 回退。
4. 群聊使用 SFU，禁止默认 Mesh。
5. 万人直播使用 Stage + CDN，不能做万人双向 WebRTC。
6. 客户端减载必须基于真实订阅、编码层和可见性策略。
7. 不得通过删除错误处理、吞异常或硬编码成功状态掩盖问题。
8. 不得在没有压测数据的情况下宣称支持万人。
9. 所有容量结论必须有指标、测试命令和环境说明。
10. 不能把密钥、Token、生产配置或录制文件提交到 Git。

## 开发步骤

### 第一阶段：架构和契约

先更新：

```text
docs/project-constitution/tasks/TASK_INDEX.md
docs/project-constitution/tasks/RTC-012-selective-subscription.md
docs/project-constitution/tasks/RTC-013-livekit-cluster.md
docs/project-constitution/tasks/RTC-014-stage-audience.md
docs/project-constitution/tasks/RTC-015-controlled-p2p.md
docs/contracts/rtc-control.openapi.yaml
docs/contracts/live-media-contract.md
```

每个任务必须包含目标、非目标、依赖、文件所有权、API/事件/状态模型、并发要求、失败恢复、安全边界、测试命令和 Definition of Done。

### 第二阶段：客户端订阅策略

完成可见参与者订阅、active speaker 质量提升、非可见视频降级、音频优先、屏幕共享优先、track 生命周期清理和远端摄像头开关同步。

### 第三阶段：服务端与部署契约

完成 LiveKit 多节点配置模板、Redis 调度契约、TURN 池配置、房间容量策略、Stage/Audience 模式、连接类型和 QoE 指标接口。

### 第四阶段：验证

执行：

```text
pnpm type-check
pnpm build
pnpm exec eslint src/modules/rtc src/components/Call.vue
相关 Java 测试
相关 Python 测试
git diff --check
```

至少增加以下测试：

- 1 对 1 订阅策略测试
- 8 人房间订阅预算测试
- active speaker 切换测试
- 摄像头关闭/重开同步测试
- P2P 失败回退 SFU 测试
- Stage 上麦幂等测试
- 观众不进入互动房间测试
- 多节点房间分片测试
- 权限和租户隔离测试

如果本机无法执行浏览器双端测试，必须明确记录未执行原因和手工验收步骤，不得声称浏览器验证通过。

## 提交规则

每个逻辑模块单独提交：

```text
feat(RTC-012): add selective subscription policy
feat(RTC-013): add LiveKit cluster contracts
feat(RTC-014): add stage audience topology
feat(RTC-015): add controlled p2p fallback
test(RTC-012): add subscription policy tests
docs(RTC): update media scale runbook
```

提交前检查：

```text
git show --stat --oneline <commit>
git diff <parent>..<commit>
git status --short
```

禁止使用：

```text
git reset --hard
git checkout --
git clean -fd
git push --force
```

## 最终报告

最终报告必须包含完成的 RTC 任务、实际修改文件、客户端减载策略、P2P 启用条件和回退路径、多节点及 Stage/CDN 部署要求、测试命令及真实结果、未完成项、剩余风险和每个提交的完整哈希。

不要只写设计方案。凡是契约已经明确且风险可控的部分，要完成代码、测试、文档和提交；凡是需要生产基础设施的部分，要提供可执行配置和验收标准。
```

