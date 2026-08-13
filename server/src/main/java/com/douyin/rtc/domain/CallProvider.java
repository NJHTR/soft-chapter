package com.douyin.rtc.domain;

/**
 * 通话媒体 provider (CALL_DOMAIN_MODEL.md §1)。
 * 实体中以字符串存储语义值: livekit / p2p-fallback / legacy。
 */
public enum CallProvider {
    LIVEKIT("livekit"),
    P2P_FALLBACK("p2p-fallback"),
    LEGACY("legacy");

    private final String code;

    CallProvider(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static CallProvider fromCode(String code) {
        for (CallProvider p : values()) {
            if (p.code.equals(code)) {
                return p;
            }
        }
        throw new IllegalArgumentException("未知 provider: " + code);
    }
}