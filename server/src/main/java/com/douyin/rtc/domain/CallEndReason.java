package com.douyin.rtc.domain;

/**
 * 通话结束原因 (CALL_DOMAIN_MODEL.md §1 end_reason 枚举)。
 * 注意: CANCELLED 状态无对应结束原因(end_reason 留空);
 * NEGOTIATING 超时失败时 end_reason = EXPIRED。
 */
public enum CallEndReason {
    HANGUP,
    REJECTED,
    EXPIRED,
    FAILED,
    PERMISSION,
    PROVIDER
}