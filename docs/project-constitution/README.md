# 项目宪法导航

| 文件 | 用途 |
|---|---|
| [EXECUTION_PROMPT.md](./EXECUTION_PROMPT.md) | 可直接复制给主开发智能体的执行提示词（勘察、分波次、提交与结束条件） |
| [PROJECT_CONSTITUTION.md](./PROJECT_CONSTITUTION.md) | 长期架构原则、模块所有权、硬阻断和合并门禁 |
| [TASK_PROTOCOL.md](./TASK_PROTOCOL.md) | 任务生命周期、分波次开发、智能体协作和完成条件 |
| [AGENTGIT_PROTOCOL.md](./AGENTGIT_PROTOCOL.md) | 快照、命令、提交、审查和集成审计证据 |
| [state/PROJECT_STATE.yaml](./state/PROJECT_STATE.yaml) | 当前分支、架构决策、任务依赖和验证状态 |
| [state/WORK_LOG.md](./state/WORK_LOG.md) | 现状勘察、决策、风险和下一步工作记录 |
| [tasks/TASK_INDEX.md](./tasks/TASK_INDEX.md) | 所有 RTC 任务和波次依赖 |
| [tasks/RTC-001-foundation.md](./tasks/RTC-001-foundation.md) | 当前首个基础任务的边界和 DoD |

任何实现任务开始前必须按 `TASK_PROTOCOL.md` 读取这些文件，并在状态文件和工作日志中留下可回放记录。
