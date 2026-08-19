# AI-002：Agent Runtime 与 Model Gateway

owner: main-dev-agent, created: 2026-08-14

## 状态

- 状态：`planned`
- 依赖：AI-001
- 拥有：`com.douyin.ai.runtime`、`com.douyin.ai.gateway`、`com.douyin.ai.provider`（新建 Java 包）
- 禁止修改：RTC 域、用户未提交工作区；`AIController` 仅允许在本任务内做兼容性改造（保持 `/api/ai/chat` 响应结构不变）

## 目标

- Agent Runtime：意图/工具/上下文预算/超时重试取消/trace_id/幂等/权限（P0 范围见 [../../ai-agent/ARCHITECTURE.md](../../ai-agent/ARCHITECTURE.md) M1）。
- Model Gateway：本地常驻 worker 管理（复用 `search_summary.py --serve` 协议：READY / `SUMMARY:`+`__END__` / `ERROR:` / EXIT）+ DeepSeek 远端 fallback + OpenAI-compatible provider 接口 + 健康检查 + 流式输出 + p95 延迟/显存监控（同上 M2）。

## 非目标

- 不实现反馈采集与候选集（AI-003/AI-004）。
- 不训练、不发布模型（AI-005/AI-006）。
- 8B worker 默认不启用（本机离线不可跑，见 [../../ai-agent/LOW_RESOURCE_OPTIMIZATION.md](../../ai-agent/LOW_RESOURCE_OPTIMIZATION.md)）。

## 事实基线

- 现状 `/api/ai/chat` 返回 `{reply, role}`；`SearchSuggestionService` 已实现 8B→3B 回退、`.venv/Scripts/python.exe` 优先、`HF_HUB_OFFLINE=1`（本任务复用之，新增探活/重启/队列）。
- 现状无 trace_id、无超时/重试/取消（RestTemplate 直连），全部为本任务新增。

## Definition of Done

- [ ] 单元测试：provider 重试（指数退避 ≤2 次）、超时（默认 15s 可配）、fallback 链（DeepSeek 失败→本地 worker→占位）、本地 worker 崩溃自动重启、READY 超时处理。
- [ ] `/api/ai/chat` 响应与现状完全兼容（`{reply, role}` 结构），占位回复分支语义保留但记录错误指标。
- [ ] 默认 provider 为 DeepSeek 远端（复用 `${DEEPSEEK_API_KEY}` / `${DEEPSEEK_BASE_URL}`，env 化见 AI-001）；本地 worker 可配置启用（默认随配置，不影响现状摘要链路）。
- [ ] trace_id 贯穿请求与 provider 调用，随响应头/日志（掩码规则见 [../../ai-agent/SECURITY.md](../../ai-agent/SECURITY.md)）可追踪。
- [ ] 健康检查端点（如 `/api/ai/gateway/health`）输出各 provider 状态、p95 延迟、worker 内存/显存；流式输出（SSE）为 P0 目标能力（现状无流式，属新增，需前端后续对接）。

## 交付物

- `com.douyin.ai.runtime`（ChatRequest/ChatReply/TraceContext、上下文预算、幂等键）
- `com.douyin.ai.gateway`（Gateway、健康检查、指标采集）
- `com.douyin.ai.provider`（AIProvider 接口、DeepSeekRemoteProvider、LocalWorkerProvider、LocalWorkerManager）