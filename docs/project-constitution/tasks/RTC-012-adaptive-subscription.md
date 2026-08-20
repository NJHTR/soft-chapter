# RTC-012：客户端选择性订阅与媒体减载

## 状态与边界

- 状态：`in_progress`（实现已提交 `43a87c5`/`dd7d4b6`；DoD 中真实 2/4/8 浏览器矩阵、adaptiveStream、SFU egress 量化未执行，不得标记 completed）
- 负责人：主智能体（Wave C）
- 分支/开始时间：`dev/full` / 2026-08-20
- 波次：C
- 依赖：RTC-005、RTC-007
- 前置证据：`8d6939e`、`f1d8bf9` 仅实现基础端口和面板策略，不代表任务完成
- 负责目录：`src/modules/rtc/adapter/`、`src/modules/rtc/quality/`、
  `src/modules/rtc/store/`、群聊视图和契约测试
- 当前禁止拥有：用户未提交的 `src/modules/rtc/components/CallPanel.vue`；须待用户改动独立提交或明确
  文件所有权后才能增量修改
- 禁止修改：群聊 mesh、客户端 relay、未验证的 `MediaStream`/`RemoteTrack` 双重绑定；
  在 attach/detach 验收前开启 adaptiveStream

## 目标与非目标

补齐 publication registry、元素可见性、前后台和房间 generation 契约，在不破坏现有 1 对 1 的
前提下实现 simulcast、dynacast、可见 tile/主画面/active speaker、屏幕共享优先、后台视频暂停和
音频优先。所有减载必须真实调用 provider subscription/quality API。

非目标：不使用 CSS/DOM 隐藏冒充退订；不把 adaptiveStream 的布尔开关当完成；不实现 mesh。

## Provider-neutral 契约

- 统一契约：`docs/contracts/rtc-scale-control-contract.md` 第 4 节。
- Join 显式携带 `scope=direct|group|stage`，不得用当前远端人数猜测拓扑。
- Registry 以 `(participant_id, publication_id, source)` 管理 microphone、camera、screen share 和
  screen audio；participant leave、publication replace 和 source 恢复都使用同一复合键。
- Adapter 只有期望订阅/质量变化时才调用 LiveKit `setSubscribed` / `setVideoQuality`。
- 屏幕共享保持 `HIGH` 且优先于摄像头；主画面和 active speaker 为 `HIGH`，普通可见 tile 默认
  `MEDIUM`，弱网或人数策略可降 `LOW`。
- 隐藏、最小化、后台默认暂停普通摄像头视频并保持音频。
- 迟到 mute/unsubscribe、摄像头重开、screen share 结束和 participant leave 必须按 room
  generation 幂等清理，旧 ended track 不得覆盖新轨道。

## 最小实现顺序

1. 增加默认关闭的 `RTC_SELECTIVE_SUBSCRIPTION_ENABLED` 和纯策略/rollback 单测。
2. 扩展 `RtcMediaPortJoinOptions` 的显式 scope 和 publication-level port，不泄漏 LiveKit 类型。
3. Adapter 增加 TrackPublished/Unpublished、按 source registry、差异化 provider 调用和显式 listener cleanup。
4. Store 驱动 page visibility/minimized context，避免把非响应式 document 状态缓存进 computed。
5. 完成 mock adapter 生命周期测试后，再处理 CallPanel tile visibility/主画面；最后单独验收
   RemoteTrack attach/detach 并评估 adaptiveStream。

## 验证命令

```text
pnpm exec vue-tsc --noEmit --pretty false
pnpm exec eslint src/modules/rtc
pnpm exec vitest run src/modules/rtc
pnpm run build-only
git diff --check
```

若仓库尚未引入测试 runner，应在本任务独立提交中引入并锁定版本，不得把未运行单测写成通过。

## Definition of Done

- [ ] `RtcMediaPort` 能按 `(participant_id, publication_id, source)` 控制音频、视频和质量层，不泄漏
  LiveKit 类型。
- [ ] `setSubscribed`、`setVideoQuality`、TrackPublished/Unpublished 和轨道清理有 provider mock 契约测试。
- [ ] direct/group/stage、未来 publication、重复策略、迟到事件、摄像头重开、屏幕共享恢复和 rejoin 测试通过。
- [ ] `adaptiveStream` 仅在真实 `RemoteTrack.attach/detach` 或等价可见性契约通过后打开。
- [ ] 1 对 1、2/4/8 人全视频、只音频、隐藏 tile、最小化、后台 tab、active speaker、
  屏幕共享、摄像头切换和弱网矩阵通过。
- [ ] 与全订阅基线相比，群聊下行、解码 CPU 和 SFU egress 的变化有真实统计。
- [ ] 回滚开关恢复 SFU 全订阅，音频连续且没有黑屏、重复绑定或监听器泄漏。

## 回滚与风险

- `RTC_SELECTIVE_SUBSCRIPTION_ENABLED=false` 恢复授权轨道全订阅；adaptiveStream 保持关闭。
- 回滚通过 registry 重放 provider 状态，不删除当前 RemoteTrack，不切换房间或拓扑。
- 已知风险：当前实现按 media kind 替换 track、visibility 非响应式缓存、重复订阅信令、缺少
  future publication 策略和 listener generation 清理；实现前不得标记完成。

## 交付记录

- 提交哈希：
  - `43a87c5 feat(RTC-012): introduce pinned vitest runner and config`
  - `dd7d4b6 feat(RTC-012): selective subscription, explicit join scope and publication registry`
    （subscriptionPolicy.ts 可见性/质量策略、livekitAdapter.ts 复合键 publication registry + change-only
    `setSubscribed`/`setVideoQuality` + TrackPublished/Unpublished/ParticipantDisconnected 幂等清理 +
    mediaAttention 暂停逻辑 + joinScope/topology、rtcMediaPort `scope` 必填、useRtcStore 状态同步）
- 测试结果：
  - `pnpm test`（vitest 3.2.4）：14/14 通过（subscriptionPolicy 8 + livekitAdapter 5 + 原有 1）
  - RTC 模块 `vue-tsc --noEmit` 无错误；`pnpm run build-only` 通过（53.68s）
  - eslint src/modules/rtc 通过
- 已验收：provider-neutral 订阅端口、quality 层变更、摄像头重开替换/忽略静音、attention 暂停、
  1:1 全订阅兼容；全部通过真实 LiveKit SDK 方法 mock 桩验证。
- 未执行：真实 2/4/8 人浏览器矩阵、隐藏 tile/最小化/后台 tab 真实带宽与客户端 CPU 变化、SFU egress 实测。
- 遗留风险：`adaptiveStream` 保持关闭（需 attach/detach 契约测试通过后开启）；CallPanel 用户改动未纳入本任务。
