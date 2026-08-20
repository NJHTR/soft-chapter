package com.douyin.rtc.observability;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.repository.RtcCallParticipantMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * QoE 摘要服务：ACL 校验（仅通话成员可上报/读取）、聚合入
 * {@link QoeAggregateStore}，并同步更新低基数指标。
 * 媒体字节/原始 stats 不进入本服务。
 */
@Service
public class QoeSummaryService {

    private static final Logger log = LoggerFactory.getLogger(QoeSummaryService.class);

    private final RtcCallParticipantMapper participantMapper;
    private final QoeAggregateStore store;
    private final CapacityMetrics metrics;

    public QoeSummaryService(RtcCallParticipantMapper participantMapper,
                             QoeAggregateStore store,
                             CapacityMetrics metrics) {
        this.participantMapper = participantMapper;
        this.store = store;
        this.metrics = metrics;
    }

    public QoeAggregateStore.CallQoe report(RtcQoeSummary summary, Long userId) {
        if (summary == null || summary.callId() == null || summary.callId().isBlank()) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "缺少 call_id");
        }
        if (summary.sampledAt() == null || summary.aggregation() == null) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "缺少 sampled_at/aggregation");
        }
        var membership = participantMapper.findByCallAndUser(summary.callId(), userId);
        if (membership == null) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED, "非通话成员，无法上报 QoE");
        }
        int participantCount = 0;
        List<String> roster = List.of();
        var participants = participantMapper.listByCall(summary.callId());
        if (participants != null) {
            participantCount = participants.size();
            roster = new ArrayList<>();
            for (var participant : participants) {
                roster.add(String.valueOf(participant.getUserId()));
            }
        }
        validateAggregation(summary.aggregation());
        store.put(summary.callId(), summary, participantCount);
        metrics.observeQoe(summary, roster);
        log.debug("[QOE] summary accepted callId={} samples={} rttMs={} loss={}",
                summary.callId(), store.get(summary.callId()) == null ? 0 : store.get(summary.callId()).samples(),
                summary.aggregation().getOrDefault(RtcQoeSummary.AVG_RTT_MS, 0.0),
                summary.aggregation().getOrDefault(RtcQoeSummary.LOSS_RATIO, 0.0));
        return store.get(summary.callId());
    }

    public Map<String, Object> summaryFor(Long userId, String callId) {
        if (callId == null || callId.isBlank()) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "缺少 call_id");
        }
        var membership = participantMapper.findByCallAndUser(callId, userId);
        if (membership == null) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED, "非通话成员，无法读取 QoE");
        }
        QoeAggregateStore.CallQoe qoe = store.get(callId);
        if (qoe == null) return Map.of("call_id", callId, "available", false);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("call_id", callId);
        out.put("available", true);
        out.put("samples", qoe.samples());
        out.put("participant_count", qoe.participantCount());
        out.put("rtt_ms_avg", qoe.avg(RtcQoeSummary.AVG_RTT_MS));
        out.put("loss_ratio", qoe.avg(RtcQoeSummary.LOSS_RATIO));
        out.put("jitter_ms_avg", qoe.avg(RtcQoeSummary.JITTER_MS_AVG));
        out.put("freeze_ratio", qoe.avg(RtcQoeSummary.FREEZE_RATIO));
        out.put("fps_avg", qoe.avg(RtcQoeSummary.FPS_AVG));
        out.put("first_frame_ms", qoe.avg(RtcQoeSummary.FIRST_FRAME_MS));
        out.put("reconnects", qoe.avg(RtcQoeSummary.RECONNECTS));
        out.put("nack_pli_fir_total", qoe.avg(RtcQoeSummary.NACK_PLI_FIR));
        out.put("egress_mbps_publish", qoe.avg(RtcQoeSummary.EGRESS_MBPS_PUBLISH));
        out.put("egress_mbps_subscribe", qoe.avg(RtcQoeSummary.EGRESS_MBPS_SUBSCRIBE));
        out.put("cpu_load", qoe.avg(RtcQoeSummary.CPU_LOAD));
        out.put("turn_relay_ratio", qoe.avg(RtcQoeSummary.TURN_RELAY_RATIO));
        return out;
    }

    /** 摘要只允许已知聚合键，防伪造/超大 payload 进入指标。 */
    private static void validateAggregation(Map<String, Double> aggregation) {
        if (aggregation.size() > 32) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "聚合字段过多");
        }
        for (Map.Entry<String, Double> entry : aggregation.entrySet()) {
            if (!KNOWN_KEYS.contains(entry.getKey())) {
                throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT,
                        "未知聚合键 " + entry.getKey());
            }
            Double value = entry.getValue();
            if (value == null || !Double.isFinite(value) || value < 0) {
                throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT,
                        "聚合值非法 " + entry.getKey());
            }
        }
    }

    private static final List<String> KNOWN_KEYS = List.of(
            RtcQoeSummary.AVG_RTT_MS, RtcQoeSummary.LOSS_RATIO,
            RtcQoeSummary.JITTER_MS_AVG, RtcQoeSummary.FREEZE_RATIO,
            RtcQoeSummary.FPS_AVG, RtcQoeSummary.FIRST_FRAME_MS,
            RtcQoeSummary.RECONNECTS, RtcQoeSummary.NACK_PLI_FIR,
            RtcQoeSummary.EGRESS_MBPS_PUBLISH, RtcQoeSummary.EGRESS_MBPS_SUBSCRIBE,
            RtcQoeSummary.CPU_LOAD, RtcQoeSummary.TURN_RELAY_RATIO
    );

    public static Duration defaultRetention() {
        return Duration.ofMinutes(15);
    }
}