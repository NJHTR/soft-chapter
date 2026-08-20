package com.douyin.rtc.p2p;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RTC-015 P2P 实验指标(低基数,rtc_p2p_ 前缀,与 CapacityMetrics 同一注册模式)。
 * registry 未配置时降级 no-op,控制面不因指标依赖阻塞。
 */
@Component
public class P2pMetrics {

    private final MeterRegistry registry;
    private final Map<String, Counter> attemptCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> fallbackCounters = new ConcurrentHashMap<>();
    private final Counter consentPairs;

    public P2pMetrics(@Autowired(required = false) MeterRegistry registry) {
        this.registry = registry;
        this.consentPairs = registry == null ? null
                : Counter.builder("rtc_p2p_consent_pairs_total").register(registry);
    }

    /** outcome: direct|srflx|relay|sfu */
    public void recordAttempt(String outcome) {
        if (registry == null) return;
        attemptCounters.computeIfAbsent(String.valueOf(outcome), key -> Counter.builder("rtc_p2p_attempts_total")
                .tags("outcome", key).register(registry)).increment();
    }

    /** reason: P2pFallbackReason 枚举名 */
    public void recordFallback(String reason) {
        if (registry == null) return;
        fallbackCounters.computeIfAbsent(String.valueOf(reason), key -> Counter.builder("rtc_p2p_fallbacks_total")
                .tags("reason", key).register(registry)).increment();
    }

    public void recordConsentPair() {
        if (consentPairs != null) {
            consentPairs.increment();
        }
    }
}