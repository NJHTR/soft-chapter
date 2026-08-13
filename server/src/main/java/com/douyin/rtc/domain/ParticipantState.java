package com.douyin.rtc.domain;

/**
 * 通话参与者状态机 (CALL_DOMAIN_MODEL.md §3)。
 * 终态集合: REJECTED / CANCELLED / FAILED / LEFT。
 */
public enum ParticipantState {
    INVITED,
    RINGING,
    JOINING,
    CONNECTED,
    RECONNECTING,
    LEFT,
    REJECTED,
    CANCELLED,
    FAILED
}