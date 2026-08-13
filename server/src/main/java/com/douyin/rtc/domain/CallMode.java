package com.douyin.rtc.domain;

/**
 * 通话媒体模式 (signaling.schema.json payload.mode)。
 * 实体中以字符串存储语义值: audio / video。
 */
public enum CallMode {
    AUDIO("audio"),
    VIDEO("video");

    private final String code;

    CallMode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static CallMode fromCode(String code) {
        for (CallMode m : values()) {
            if (m.code.equals(code)) {
                return m;
            }
        }
        throw new IllegalArgumentException("未知通话模式: " + code);
    }
}