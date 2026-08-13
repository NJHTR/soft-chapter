package com.douyin.rtc.domain;

/**
 * 通话参与者生命周期命令 (CALL_DOMAIN_MODEL.md §3)。
 * ring/connected/fail 由系统触发; join/reject/cancel/leave/reconnect 由客户端或系统触发。
 */
public enum ParticipantCommand {
    RING,
    JOIN,
    CONNECTED,
    RECONNECT,
    LEAVE,
    REJECT,
    CANCEL,
    FAIL
}