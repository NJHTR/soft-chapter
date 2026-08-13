package com.douyin.rtc.domain;

/**
 * 通话会话生命周期命令 (CALL_DOMAIN_MODEL.md §2 转移表列)。
 * create/accept/reject/cancel/hangup 由客户端触发;
 * expire/ended/fail 只能由系统(worker、provider webhook)触发。
 */
public enum CallCommand {
    CREATE,
    ACCEPT,
    REJECT,
    CANCEL,
    EXPIRE,
    NEGOTIATE_START,
    CONNECTED,
    HANGUP,
    ENDED,
    FAIL
}