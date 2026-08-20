package com.douyin.rtc.capacity;

import java.util.Locale;

/**
 * 容量维度固定枚举（低基数）。Prometheus 只允许使用这些维度作为 label，
 * room/user/call 明细不得进入指标标签。
 */
public enum CapacityDimension {
    ROOMS,
    PUBLISHERS,
    SUBSCRIBERS,
    SUBSCRIBED_TRACKS,
    CONNECTIONS,
    EGRESS_MBPS,
    RTP_PPS,
    CPU_PERCENT,
    MEMORY_PERCENT,
    TURN_RELAY_MBPS;

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}