package com.douyin.rtc.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 客户端 2～5 秒聚合的 QoE 摘要（RTC-011 契约第 3 节）。
 * 只允许聚合值进入控制面；原始 RTP/高频 stats 不写 Kafka/聊天 WS。
 */
public record RtcQoeSummary(
        String traceId,
        String callId,
        List<String> participantIds,
        String nodeId,
        String topology,
        Instant sampledAt,
        Map<String, Double> aggregation
) {

    /** 低基数聚合键：rtt_ms_avg / loss_ratio / jitter_ms_avg / freeze_ratio / fps_avg */
    public static final String AVG_RTT_MS = "rtt_ms_avg";
    public static final String LOSS_RATIO = "loss_ratio";
    public static final String JITTER_MS_AVG = "jitter_ms_avg";
    public static final String FREEZE_RATIO = "freeze_ratio";
    public static final String FPS_AVG = "fps_avg";
    public static final String FIRST_FRAME_MS = "first_frame_ms";
    public static final String RECONNECTS = "reconnects";
    public static final String NACK_PLI_FIR = "nack_pli_fir_total";
    public static final String EGRESS_MBPS_PUBLISH = "egress_mbps_publish";
    public static final String EGRESS_MBPS_SUBSCRIBE = "egress_mbps_subscribe";
    public static final String CPU_LOAD = "cpu_load";
    public static final String TURN_RELAY_RATIO = "turn_relay_ratio";
}