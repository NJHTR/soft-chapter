package com.douyin.rtc.domain;

/**
 * 事件账本 kind 枚举。客户端命令映射到 signaling.schema.json 的 kind 值;
 * 系统内部转移(状态迁移/超时/收敛)使用 call.state / call.connected /
 * call.expired / call.ended / call.failed,不经过 wire envelope。
 */
public enum CallEventKind {

    CALL_REQUEST("call.request"),
    CALL_ACCEPT("call.accept"),
    CALL_REJECT("call.reject"),
    CALL_CANCEL("call.cancel"),
    CALL_JOIN("call.join"),
    CALL_LEAVE("call.leave"),
    CALL_HANGUP("call.hangup"),
    CALL_PARTICIPANT_UPDATE("call.participant.update"),
    CALL_STATE("call.state"),
    CALL_CONNECTED("call.connected"),
    CALL_ENDED("call.ended"),
    CALL_EXPIRED("call.expired"),
    CALL_FAILED("call.failed");

    private final String wire;

    CallEventKind(String wire) {
        this.wire = wire;
    }

    public String getWire() {
        return wire;
    }
}