package com.douyin.rtc.observability;

import com.douyin.rtc.capacity.AdmissionDecision;
import com.douyin.rtc.capacity.CapacityDimension;
import com.douyin.rtc.capacity.CapacityNodeConfig;
import com.douyin.rtc.capacity.CapacityProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RTC 容量/QoE 低基数指标（RTC-011）。
 * 标签只允许 region/provider/codec/version/node/dimension/reason 等低基数枚举；
 * room/user/call/trace 一律不进 Prometheus，只进受控聚合存储。
 *
 * <p>ratio 指标（observed/limit）供 Prometheus rules 直接告警：
 * 70% 持续 5 分钟扩容告警，80% 紧急硬门禁（与 AdmissionService 决策一致）。
 */
@Component
public class CapacityMetrics {

    private final MeterRegistry registry;
    private final CapacityProperties properties;
    private final AtomicLong rooms = new AtomicLong(0);
    private final AtomicLong publishers = new AtomicLong(0);
    private final AtomicLong subscribers = new AtomicLong(0);
    private final AtomicLong egressMbps = new AtomicLong(0);
    private final AtomicLong rtpPps = new AtomicLong(0);
    private final AtomicLong cpuPercent = new AtomicLong(0);
    private final AtomicLong memoryPercent = new AtomicLong(0);
    private final AtomicLong rttMs = new AtomicLong(0);
    private final AtomicLong lossRatio = new AtomicLong(0);
    private final AtomicLong reconnects = new AtomicLong(0);
    private final AtomicLong nackPliFir = new AtomicLong(0);
    private final AtomicLong firstFrameMs = new AtomicLong(0);
    private final AtomicLong freezeRatio = new AtomicLong(0);
    private final AtomicLong turnRelayRatio = new AtomicLong(0);
    private final Map<String, Counter> decisionCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> qoeCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Double>> observedByNodeDim = new ConcurrentHashMap<>();
    private final Map<String, Double> limitByNodeDim = new ConcurrentHashMap<>();

    public CapacityMetrics(@Autowired(required = false) MeterRegistry registry) {
        // Micrometer 未引入/未配置时降级为 no-op，不阻塞控制面。
        this(registry, null);
    }

    @Autowired
    public CapacityMetrics(@Autowired(required = false) MeterRegistry registry,
                           @Autowired(required = false) CapacityProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @PostConstruct
    void registerGauges() {
        if (registry == null) return;
        Gauge.builder("rtc_capacity_rooms", rooms, AtomicLong::get)
                .description("active LiveKit rooms").register(registry);
        Gauge.builder("rtc_capacity_publishers", publishers, AtomicLong::get).register(registry);
        Gauge.builder("rtc_capacity_subscribers", subscribers, AtomicLong::get).register(registry);
        Gauge.builder("rtc_capacity_egress_mbps", egressMbps, AtomicLong::get).register(registry);
        Gauge.builder("rtc_capacity_rtp_pps", rtpPps, AtomicLong::get).register(registry);
        Gauge.builder("rtc_capacity_cpu_percent", cpuPercent, AtomicLong::get).register(registry);
        Gauge.builder("rtc_capacity_memory_percent", memoryPercent, AtomicLong::get).register(registry);
        Gauge.builder("rtc_qoe_rtt_ms", rttMs, AtomicLong::get).register(registry);
        Gauge.builder("rtc_qoe_loss_ratio", lossRatio, AtomicLong::get).register(registry);
        Gauge.builder("rtc_qoe_reconnects_total", reconnects, AtomicLong::get).register(registry);
        Gauge.builder("rtc_qoe_nack_pli_fir_total", nackPliFir, AtomicLong::get).register(registry);
        Gauge.builder("rtc_qoe_first_frame_ms", firstFrameMs, AtomicLong::get).register(registry);
        Gauge.builder("rtc_qoe_freeze_ratio", freezeRatio, AtomicLong::get).register(registry);
        Gauge.builder("rtc_turn_relay_ratio", turnRelayRatio, AtomicLong::get).register(registry);
    }

    public void observeDimension(String nodeId, CapacityDimension dim, double value) {
        observedByNodeDim.computeIfAbsent(ratioKey(nodeId, dim), ignored -> new AtomicReference<>())
                .set(Math.max(0.0, value));
        if (registry == null) return;
        Tags tags = Tags.of("node", nodeId, "dimension", dim.label());
        Gauge.builder("rtc_node_dimension_observed", () -> value)
                .tags(tags)
                .register(registry);
        registerLimitAndRatioGauges(nodeId, dim);
    }

    /** 按配置上限注册 limit/ratio 对（低基数：node × dimension）。 */
    private void registerLimitAndRatioGauges(String nodeId, CapacityDimension dim) {
        if (registry == null || properties == null) return;
        String key = ratioKey(nodeId, dim);
        Double limit = limitByNodeDim.computeIfAbsent(key, ignored -> {
            for (CapacityNodeConfig node : properties.getNodes()) {
                if (node.getNodeId() != null && node.getNodeId().equals(nodeId)) {
                    Double configured = node.getLimits().get(dim);
                    return configured != null && configured > 0 ? configured : Double.NaN;
                }
            }
            return Double.NaN;
        });
        if (limit.isNaN()) return;
        Tags tags = Tags.of("node", nodeId, "dimension", dim.label());
        Gauge.builder("rtc_capacity_limit", () -> limit).tags(tags).register(registry);
        Gauge.builder("rtc_capacity_ratio",
                        () -> observedByNodeDim.getOrDefault(key, new AtomicReference<>(0.0)).get() / limit)
                .tags(tags)
                .register(registry);
    }

    private static String ratioKey(String nodeId, CapacityDimension dim) {
        return nodeId + "|" + dim.name();
    }

    public void recordAdmission(String nodeId, AdmissionDecision decision) {
        if (registry == null) return;
        String decisionKey = nodeId + "|" + decision.decision().name() + "|" + decision.reason().code();
        decisionCounters.computeIfAbsent(decisionKey, key -> {
            String[] parts = key.split("\\|", 3);
            return Counter.builder("rtc_admission_decisions_total")
                    .tags("node", parts[0], "decision", parts[1], "reason", parts[2])
                    .register(registry);
        }).increment();
    }

    /** 客户端摘要 → 低基数指标。列表类键只取汇总值，不产生高基数时间序列。 */
    public void observeQoe(RtcQoeSummary summary, List<String> roster) {
        if (registry == null) return;
        Map<String, Double> a = summary.aggregation();
        gaugeIfPresent(a, RtcQoeSummary.AVG_RTT_MS, rttMs, "rtt");
        gaugeIfPresent(a, RtcQoeSummary.LOSS_RATIO, lossRatio, "loss");
        gaugeIfPresent(a, RtcQoeSummary.RECONNECTS, reconnects, "reconnects");
        gaugeIfPresent(a, RtcQoeSummary.NACK_PLI_FIR, nackPliFir, "nack_pli_fir");
        gaugeIfPresent(a, RtcQoeSummary.FIRST_FRAME_MS, firstFrameMs, "first_frame");
        gaugeIfPresent(a, RtcQoeSummary.FREEZE_RATIO, freezeRatio, "freeze");
        gaugeIfPresent(a, RtcQoeSummary.TURN_RELAY_RATIO, turnRelayRatio, "turn_relay");
        qoeCounters.computeIfAbsent("samples", key -> Counter.builder("rtc_qoe_samples_total")
                .tags("source", "client_summary").register(registry)).increment();
    }

    public void recordRoomCounts(int rooms, int publishers, int subscribers) {
        this.rooms.set(rooms);
        this.publishers.set(publishers);
        this.subscribers.set(subscribers);
    }

    public void recordCapacityRatios(double egressMbps, double rtpPps,
                                     double cpuPercent, double memoryPercent) {
        this.egressMbps.set(Math.round(egressMbps));
        this.rtpPps.set(Math.round(rtpPps));
        this.cpuPercent.set(Math.round(cpuPercent));
        this.memoryPercent.set(Math.round(memoryPercent));
    }

    private static void gaugeIfPresent(Map<String, Double> a, String key,
                                       AtomicLong out, String label) {
        Double value = a.get(key);
        if (value != null) out.set(Math.round(value));
    }
}