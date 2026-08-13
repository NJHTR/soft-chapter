package com.douyin.rtc.domain;

/**
 * 通话会话服务端权威状态 (CALL_DOMAIN_MODEL.md §2)。
 * 终态集合: REJECTED / CANCELLED / EXPIRED / FAILED / ENDED。
 */
public enum CallState {
    CREATED,
    RINGING,
    ACCEPTED,
    NEGOTIATING,
    CONNECTED,
    ENDING,
    REJECTED,
    CANCELLED,
    EXPIRED,
    FAILED,
    ENDED
}