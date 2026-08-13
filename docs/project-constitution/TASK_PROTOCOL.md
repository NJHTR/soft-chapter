# 实时媒体任务协议

## 1. 任务生命周期

任务状态按以下顺序流转：

`planned -> claimed -> in_progress -> review -> integrated -> verified -> completed`

异常状态为 `blocked`，只有同一外部阻断连续三次无法推进且已经记录尝试后才能使用。状态、负责人、分支、依赖、开始时间、验证命令和提交哈希必须写入任务文件与 `PROJECT_STATE.yaml`。

## 2. 任务文件模板

每个任务至少包含：

- 目标与非目标
- 依赖任务和波次
- 负责目录、允许修改目录、禁止修改目录
- 对外 API、事件、数据和错误契约
- 状态机、并发、幂等、TTL、恢复和权限要求
- 测试命令、联调环境和 Definition of Done
- 风险、回滚方式、退役日期（若为迁移任务）

## 3. 开发前检查

主智能体或受派智能体在代码修改前必须读取：

1. `docs/project-constitution/state/PROJECT_STATE.yaml`
2. `docs/project-constitution/state/WORK_LOG.md`
3. `docs/project-constitution/PROJECT_CONSTITUTION.md`
4. `docs/project-constitution/TASK_PROTOCOL.md`
5. `docs/project-constitution/AGENTGIT_PROTOCOL.md`
6. `docs/project-constitution/tasks/TASK_INDEX.md`
7. 当前任务文件及相关 `.ai/`、`.ai-company/` 记录

同时检查工作区、当前分支、最近提交、相关历史、构建配置、测试和未提交修改。现有脏工作区必须被记录和避让。

## 4. 分波次规则

### Wave A：现状与契约

只读探索、领域模型、事件 schema、API 兼容方案和测试缺口。不得在没有契约批准时改业务代码。

### Wave B：控制面基础

实现房间/通话状态机、版本化 envelope、鉴权、幂等、事件持久化、token 签发和 provider port。媒体 provider 仍由适配器隔离。

### Wave C：媒体面

接入 LiveKit SFU、coturn、SRS/WHIP/WHEP，完成真实浏览器媒体路径。媒体和控制通道分开验证。

### Wave D：客户端与质量

拆分 Call UI，接入设备管理、simulcast/ABR、QoE、重连、弱网降级和直播 fallback。前端状态来自服务端事件和 provider 状态。

### Wave E：录制、运营与安全

接入 Egress/DVR、异步转码、对象存储、告警、审计、限流、隐私和管理员观察面。

### Wave F：独立审查与发布

执行契约、浏览器、NAT/TURN、弱网、长通话、负载、故障注入和回滚演练。审查者不能把自己唯一编写的代码当作独立审查。

依赖未完成时不得提前启动后续波次；联调失败要创建诊断任务，不能重复执行同一失败命令伪装恢复。

## 5. 智能体协作要求

每个智能体的分派消息必须写明：输入、输出、拥有目录、禁止目录、依赖、验证命令和交付标准。完成时报告：提交哈希、diff 摘要、测试结果、未解决风险和下一步。

主智能体必须逐提交检查：

```text
git show --stat --oneline <commit>
git diff <parent>..<commit>
相关模块测试
契约测试
安全与边界检查
```

共享工作区下若无法建立独立 worktree，必须采用文件所有权隔离；任何公共契约改动由主智能体统一合并。

## 6. 完成条件

任务只有在代码/文档已提交、相关测试通过、契约和工作日志更新、风险有归属、没有未解释的临时产物，并且真实提交哈希写入状态文件后，才能标记 `completed`。
