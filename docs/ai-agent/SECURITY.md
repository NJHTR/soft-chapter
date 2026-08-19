# 安全与密钥轮换风险记录（SECURITY）

owner: main-dev-agent, created: 2026-08-14

## 1. 目的与原则

登记已发现的硬编码密钥风险、处置方案与审计规则。**绝不允许把真实密钥写入任何新提交**；本文件可安全提交（仅掩码）。完整值不在此出现——完整值仍位于本地 worktree 的 `server/src/main/resources/application.yml` 等文件，必须通过轮换失效并迁移到环境变量。

## 2. 已发现硬编码密钥清单（勘察事实，精确到行号）

> 掩码约定：仅保留前 4 字符 + `••••••••••`；完整值见本地文件（不可入库）。

### 2.1 `server/src/main/resources/application.yml`

| 行号 | 配置项 | 内容（掩码） | 风险 |
|---|---|---|---|
| L19 | `spring.mail.password` | `yccv••••••••••` | 邮箱 SMTP 授权码明文入库 |
| L39 | `jwt.secret` | `ZG91••••••••••` | JWT 签名密钥明文；泄露可伪造登录态 |
| L71 | `spring.data.redis.password` | `XrKk••••••••••` | Redis 密码明文（附注：与 DB/MinIO 复用同一值） |
| L83 | `deepseek.api-key` | `sk-2e82••••••••••` | DeepSeek API 密钥明文，可被冒刷费用 |
| L90-91 | `minio.access-key` / `minio.secret-key` | `njhtr` / `XrKk••••••••••` | 对象存储凭证明文；secret-key 与 Redis/DB 同值 |

### 2.2 `server/python/export_training_data.py`

- L25-32：`DB_CONFIG` 内联 MySQL 连接参数（host `8.134.23.170`、user `dev`、password 明文 L29 `XrKk••••••••••`、database `douyin`）。该脚本输出无 PII 脱敏的训练数据（[DATA_CONTRACT.md](./DATA_CONTRACT.md) 第 4 节），不作为新闭环数据源。

### 2.3 `server/python/distill/config.py`

- L8-15：`DB_CONFIG` 内联 MySQL 明文密码（L12，与上同值，host/port/user/database 同 2.2）。
- L18-25：`DEEPSEEK_CONFIG` 内联教师模型 API key（L19，`sk-0da9••••••••••`，base_url `https://api.deepseek.com`）。该文件为蒸馏流水线共享配置，任何新提交不得携带该值。

### 2.4 `server/python/autodl_upload.zip`（未提交工作区，只读）

- 打包产物可能内含上述密钥与模型产物：**建议删除或重新打包**（剔除 `config.py`、`export_training_data.py` 等含密文件），禁止将其内容再次提交。删除/重打包由仓库所有者决定，本文档仅登记建议。

### 2.5 关联风险（附注）

- Redis、MinIO、MySQL 三处复用同一密码（`XrKk••••••••••`）：单点泄露即全盘受控；轮换必须一次性全部更换。
- `application.example.yml` 已存在：迁移目标是把真实值从 `application.yml` 移出，示例文件只保留 `${ENV:default}` 形式。

## 3. 处置：迁移到环境变量

迁移清单（AI-001 DoD；`application.yml` 仅允许密钥 env 化这一项修改）：

| 现值配置项 | 环境变量名 | 默认值（示例文件） |
|---|---|---|
| `deepseek.api-key` | `DEEPSEEK_API_KEY` | 空（空则走占位/禁用分支，现状语义） |
| `deepseek.base-url` | `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` |
| `spring.mail.password` | `MAIL_PASSWORD` | 空 |
| `jwt.secret` | `JWT_SECRET` | 空（生产启动校验非空，fail-closed） |
| `minio.access-key` | `MINIO_ACCESS_KEY` | 空 |
| `minio.secret-key` | `MINIO_SECRET_KEY` | 空 |
| `spring.data.redis.password` | `REDIS_PASSWORD` | 空 |
| Python DB（`export_training_data.py`、`distill/config.py`） | `DB_HOST` / `DB_PORT` / `DB_USER` / `DB_PASSWORD` | 空 |
| Python DeepSeek（`distill/config.py`） | `DEEPSEEK_API_KEY` | 空 |

规则：代码/配置只读 `System.getenv` 或 `${ENV:...}`；仓库内任何文件不得出现 `sk-` 或 `XrKk` 开头的真实值；测试环境变量用法写入 `application.example.yml` 注释与 AI-001 DoD。

启动方式示例（部署侧，不入库）：

```bash
export DEEPSEEK_API_KEY='<轮换后的新值>'     # 只存在于 shell/部署编排/密钥管理
export MAIL_PASSWORD='...' JWT_SECRET='...'
export MINIO_ACCESS_KEY='...' MINIO_SECRET_KEY='...' REDIS_PASSWORD='...'
export DB_HOST=... DB_PORT=... DB_USER=... DB_PASSWORD='...'
java -jar douyin-server.jar          # Spring 以 ${ENV:...} 引用
```

- 本地开发：`.env` 文件（`.gitignore` 已排除或交由仓库所有者确认）与本文件第 6 节响应流程配合使用。

## 4. 密钥轮换计划

| 序号 | 密钥 | 动作 | 时机 |
|---|---|---|---|
| 1 | DeepSeek API key（`sk-2e82`… 与 `sk-0da9`… 两把） | 立即在 DeepSeek 控制台作废并重新签发；新值只进环境变量 | AI-001 落地前 |
| 2 | MySQL / Redis / MinIO 共用密码（`XrKk`…） | 三系统同批换新且互不相同；改后更新部署环境变量与 Python 脚本读取 | AI-001 落地前 |
| 3 | `spring.mail.password` | 重发 SMTP 授权码 | AI-001 落地前 |
| 4 | `jwt.secret` | 生成新随机值（≥32 字节）；存量 token 会失效，需在发布窗口内完成 | AI-001 后首个发布窗口 |

轮换完成后：`git grep` 验证仓库无真实值；旧值记录到（仅本地、不入库的）运维交接单。

## 5. 审计规则与 fail-closed

- **提交前 grep（建议 pre-commit / 主智能体审阅清单）**：禁止出现 `sk-[A-Za-z0-9]{16,}`、`XrKk` 开头长串、SMTP 授权码形态的值；命中即阻断提交。
- 扫描命令示例（入库前执行，结果不得出现真实值）：
  ```bash
  git grep -nE 'sk-[A-Za-z0-9]{16,}'            # API key
  git grep -nE 'XrKk[^[:space:]]{20,}'          # 共用 DB/Redis/MinIO 密码
  git grep -nE 'yccv[[:alnum:]]+'               # 邮件授权码
  ```
  另建 `docs/ai-agent/security-scanner.(py|ps1)`（设计，AI-001 交付）统一执行以上规则并输出报告。
- **日志**：禁止 `log.*(apiKey|password|secret|token)` 输出值；只允许掩码或 `[redacted]`。
- **数据落库/落盘**：`ai_feedback`、`ai_candidate_sample`、训练 JSONL 必须过 [DATA_CONTRACT.md](./DATA_CONTRACT.md) 第 4 节脱敏（含密钥模式 `sk-[A-Za-z0-9]{16,}`）。
- **fail-closed**：
  - `DEEPSEEK_API_KEY` 为空且未启用占位分支 → AI 链路显式失败/占位，不得半初始化直连；
  - `JWT_SECRET` 为空 → 服务启动失败；
  - Python DB 类脚本缺环境变量 → 拒绝连接并报错退出（现状直接读内联值，迁移后行为变化在 AI-001 记录）。
- **审计事件**：开关变更、密钥轮换、发布/回滚均留审计（[AI_AGENT_CONSTITUTION.md](./AI_AGENT_CONSTITUTION.md) 第 7 节、[EVALUATION_AND_RELEASE.md](./EVALUATION_AND_RELEASE.md) 第 6 节）。

## 6. 事故响应（设计）

1. 发现疑似泄露 → 立即作废对应密钥（DeepSeek 控制台 / DB 改密 / 邮箱重发）→ 不等待"周末再说"；
2. 审计最近变更与日志中的掩码上下文，判断泄露面；
3. 检查仓库与远端历史是否存在真实值，存在则按仓库所有者流程处理（本任务不执行 git 改写）；
4. 更新本文件风险清单并归档事件。

## 7. 与工程边界的关系

- RTC 域、用户未提交工作区（含 `autodl_upload.zip`）只读，本文档不授权改动；新代码（`com.douyin.ai.*`、`server/python/ai_pipeline.py`）从第一行起遵守本记录与宪法第 6 节。
- 本文件仅登记与处置设计；实际迁移与轮换由 AI-001 执行并在其 DoD 确认。