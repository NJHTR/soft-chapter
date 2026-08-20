package com.douyin.rtc.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QoE 摘要聚合存储（内存环形摘要）。只保存按 call 聚合的少量数值，
 * 不落原始帧数据；服务重启即丢失（开发基线），生产聚合由独立 worker
 * 双写受控聚合存储（RTC-011 契约，本环境未部署，不宣称持久化）。
 */
public class QoeAggregateStore {

    private final ConcurrentHashMap<String, CallQoe> calls = new ConcurrentHashMap<>();
    private final Duration retention;

    public QoeAggregateStore(Duration retention) {
        this.retention = retention;
    }

    public void put(String callId, RtcQoeSummary summary, int participantCount) {
        long now = System.currentTimeMillis();
        calls.compute(callId, (key, existing) -> {
            CallQoe current = existing == null
                    ? new CallQoe(now, participantCount)
                    : existing.withUpdatedAt(now);
            current.merge(summary);
            return current;
        });
    }

    public CallQoe get(String callId) {
        CallQoe qoe = calls.get(callId);
        if (qoe == null) return null;
        if (System.currentTimeMillis() - qoe.updatedAt > retention.toMillis()) {
            calls.remove(callId, qoe);
            return null;
        }
        return qoe;
    }

    public int evictExpired() {
        long cutoff = System.currentTimeMillis() - retention.toMillis();
        int removed = 0;
        for (Map.Entry<String, CallQoe> entry : calls.entrySet()) {
            if (entry.getValue().updatedAt <= cutoff && calls.remove(entry.getKey(), entry.getValue())) {
                removed++;
            }
        }
        return removed;
    }

    public int activeCount() {
        return calls.size();
    }

    /** 单个 call 的汇总视图（低基数向量）。 */
    public static final class CallQoe {
        private final long createdAt;
        private long updatedAt;
        private final int participantCount;
        private long samples;
        private double rttMsSum;
        private double lossSum;
        private double jitterMsSum;
        private double freezeSum;
        private double fpsSum;
        private double firstFrameMsSum;
        private double reconnectsSum;
        private double nackPliFirSum;
        private double egressPublishSum;
        private double egressSubscribeSum;
        private double cpuSum;
        private double turnRelaySum;

        private CallQoe(long createdAt, int participantCount) {
            this.createdAt = createdAt;
            this.updatedAt = createdAt;
            this.participantCount = participantCount;
        }

        private CallQoe withUpdatedAt(long now) {
            this.updatedAt = now;
            return this;
        }

        private void merge(RtcQoeSummary summary) {
            samples++;
            Map<String, Double> a = summary.aggregation();
            rttMsSum += num(a, RtcQoeSummary.AVG_RTT_MS);
            lossSum += num(a, RtcQoeSummary.LOSS_RATIO);
            jitterMsSum += num(a, RtcQoeSummary.JITTER_MS_AVG);
            freezeSum += num(a, RtcQoeSummary.FREEZE_RATIO);
            fpsSum += num(a, RtcQoeSummary.FPS_AVG);
            firstFrameMsSum += num(a, RtcQoeSummary.FIRST_FRAME_MS);
            reconnectsSum += num(a, RtcQoeSummary.RECONNECTS);
            nackPliFirSum += num(a, RtcQoeSummary.NACK_PLI_FIR);
            egressPublishSum += num(a, RtcQoeSummary.EGRESS_MBPS_PUBLISH);
            egressSubscribeSum += num(a, RtcQoeSummary.EGRESS_MBPS_SUBSCRIBE);
            cpuSum += num(a, RtcQoeSummary.CPU_LOAD);
            turnRelaySum += num(a, RtcQoeSummary.TURN_RELAY_RATIO);
        }

        private static double num(Map<String, Double> a, String key) {
            Double value = a.get(key);
            return value == null ? 0.0 : value;
        }

        public long createdAt() {
            return createdAt;
        }

        public long updatedAt() {
            return updatedAt;
        }

        public int participantCount() {
            return participantCount;
        }

        public long samples() {
            return samples;
        }

        public double avg(String key) {
            if (samples == 0) return 0.0;
            return switch (key) {
                case RtcQoeSummary.AVG_RTT_MS -> rttMsSum / samples;
                case RtcQoeSummary.LOSS_RATIO -> lossSum / samples;
                case RtcQoeSummary.JITTER_MS_AVG -> jitterMsSum / samples;
                case RtcQoeSummary.FREEZE_RATIO -> freezeSum / samples;
                case RtcQoeSummary.FPS_AVG -> fpsSum / samples;
                case RtcQoeSummary.FIRST_FRAME_MS -> firstFrameMsSum / samples;
                case RtcQoeSummary.RECONNECTS -> reconnectsSum / samples;
                case RtcQoeSummary.NACK_PLI_FIR -> nackPliFirSum / samples;
                case RtcQoeSummary.EGRESS_MBPS_PUBLISH -> egressPublishSum / samples;
                case RtcQoeSummary.EGRESS_MBPS_SUBSCRIBE -> egressSubscribeSum / samples;
                case RtcQoeSummary.CPU_LOAD -> cpuSum / samples;
                case RtcQoeSummary.TURN_RELAY_RATIO -> turnRelaySum / samples;
                default -> 0.0;
            };
        }
    }
}