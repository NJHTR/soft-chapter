# RTC 错误码契约(RTC-003)

错误经 `RtcExceptionHandler`(@RestControllerAdvice,只处理 `CallDomainException`)返回：

```json
{ "code": 500, "msg": "<错误码>: <错误消息>" }
```

| 错误码 | 场景 | 语义 |
|---|---|---|
| `INVALID_STATE_TRANSITION` | 状态机转移表未列出的转换 | 返回当前状态,不隐式修正 |
| `NOT_AUTHORIZED` | 未登录 / 非成员 / 非发起者 / 双方非好友 | 拒绝命令 |
| `INVALID_ARGUMENT` | 缺参数 / 呼叫自己 / token TTL 越界 | 拒绝 |
| `SESSION_NOT_FOUND` | call / participant 不存在 | 查询或命令失败 |
| `CALL_EXPIRED` | RINGING/NEGOTIATING 超时窗口外操作 | TTL worker 或命令守卫 |
| `EVENT_DUPLICATE` | 相同 event_id 重放 | 幂等返回当前状态(不报错) |
| `PROVIDER_ERROR` | token 签发失败 / provider 依赖错误 | 记录 + 回退状态 |

- 终态只接受重复查询或相同 `event_id` 重放；任何新命令返回当前状态，不创建第二条记录。
- 完整行为由 `server/src/test/java/com/douyin/rtc/` 下契约测试覆盖（TransitionTable/Idempotency/OutOfOrder/Participant/Token/Webhook）。