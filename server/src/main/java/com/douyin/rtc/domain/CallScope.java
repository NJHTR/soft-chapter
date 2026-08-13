package com.douyin.rtc.domain;

/**
 * 通话范围 (signaling.schema.json payload.scope)。
 * 实体中以字符串存储语义值: direct / group / live-interactive。
 */
public enum CallScope {
    DIRECT("direct"),
    GROUP("group"),
    LIVE_INTERACTIVE("live-interactive");

    private final String code;

    CallScope(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static CallScope fromCode(String code) {
        for (CallScope s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知通话范围: " + code);
    }
}