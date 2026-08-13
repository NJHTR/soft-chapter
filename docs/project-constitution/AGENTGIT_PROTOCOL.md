# AgentGit 审计协议

本仓库当前以 Git 提交和项目状态文件作为 AgentGit 的可审计载体。未来接入自动化 AgentGit 服务时，事件字段必须与本协议兼容。

## 1. 事件模型

每次任务执行至少产生以下逻辑事件：

| 事件 | 必填证据 |
|---|---|
| `task.claimed` | task_id、agent、branch、输入状态、依赖 |
| `snapshot.created` | commit、工作区状态、相关文件清单 |
| `command.executed` | 命令、工作目录、退出码、摘要、时间 |
| `change.proposed` | 文件边界、契约影响、风险 |
| `commit.created` | commit、父提交、stat、验证结果 |
| `review.completed` | 审查者、发现、结论、剩余风险 |
| `integration.completed` | 合并/挑选提交、跨模块测试、回滚点 |
| `task.verified` | DoD 清单、实际环境和测试产物 |

## 2. 快照规则

- 快照必须记录 `git status --short`、当前分支、HEAD 和未提交文件。
- 未提交文件不可被假设为当前任务产物；需要在工作日志中标记来源未知或用户已有。
- 二进制、密钥、token、个人数据和构建产物不得写入事件日志。
- 命令输出只保留可复现结论和失败摘要，避免把完整敏感日志提交到仓库。

## 3. 提交规则

提交信息使用：

```text
feat(<task-id>): short intent
fix(<task-id>): short intent
test(<task-id>): short intent
docs(<task-id>): short intent
```

一个提交只拥有一个任务边界；禁止把 unrelated 格式化、构建产物、密钥和用户未提交改动混入。若多个智能体共享工作区，提交前必须再次检查 staged diff。

## 4. 审查门

审查者必须独立检查：

- 是否越过控制面/媒体面边界；
- 是否引入未版本化字段、非幂等命令或权限绕过；
- 是否以 stub、echo、假统计宣称真实能力；
- 是否有测试覆盖重复、乱序、过期、重连和失败恢复；
- 是否有可执行的回滚和迁移退出日期。

没有真实 SDP/ICE/媒体路径的 WebRTC 改动，结论只能是“实验或未完成”，不能标记生产完成。
