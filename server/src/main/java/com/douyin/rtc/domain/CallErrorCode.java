package com.douyin.rtc.domain;

/**
 * 通话领域错误码。领域层(状态机)对未列出转换抛出
 * {@link #INVALID_STATE_TRANSITION},禁止隐式修正状态;
 * 服务层对乱序命令安全返回当前状态,不创建新记录。
 */
public enum CallErrorCode {
    /** 状态机未列出的转换 */
    INVALID_STATE_TRANSITION,
    /** 会话不存在 */
    SESSION_NOT_FOUND,
    /** client_request_id 幂等键已被其他用户占用 */
    SESSION_ALREADY_EXISTS,
    /** 发起者或目标参与者已经处于另一场通话中 */
    BUSY,
    /** 未登录 / 非成员 / 无权操作 */
    NOT_AUTHORIZED,
    /** 仅发起者可执行的命令被他人调用 */
    NOT_INITIATOR,
    /** 仅目标参与者可执行的命令由他人调用 */
    NOT_TARGET,
    /** RINGING 会话已过 expires_at */
    CALL_EXPIRED,
    /** 事件重复(event_id 已被消费且语义不一致) */
    EVENT_DUPLICATE,
    /** 事件序号乱序(seq 必须单调递增) */
    SEQ_OUT_OF_ORDER,
    /** 参数非法 */
    INVALID_ARGUMENT,
    /** 触发限流 */
    RATE_LIMITED,
    /** provider 或依赖服务错误 */
    PROVIDER_ERROR,
    /** RTC-011 容量门禁拒绝（超限或容量服务不可用） */
    CAPACITY_REJECTED,
    /** RTC-014 Stage 发布者到达硬上限(默认 8) */
    STAGE_LIMIT_REACHED,
    /** RTC-014 命令携带的 stage_generation 已过期(乱序旧事件不得复活成员) */
    GENERATION_STALE,
    /** RTC-015 通话当前不满足 P2P eligibility,拒绝创建 attempt */
    P2P_NOT_ELIGIBLE,
    /** RTC-015 P2P 信号或 probe 前必须双方 consent 且在 TTL 内 */
    P2P_CONSENT_REQUIRED,
    /** RTC-015 该 call_id + topology_generation 的 consent 已过期或已撤销 */
    P2P_CONSENT_EXPIRED
}
