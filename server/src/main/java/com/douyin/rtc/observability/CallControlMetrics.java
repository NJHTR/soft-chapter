package com.douyin.rtc.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Low-cardinality call control-plane metrics. Never tag callId or userId. */
@Component
public class CallControlMetrics {

    private final MeterRegistry registry;

    public CallControlMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void transition(String from, String to) {
        Counter.builder("rtc.call.state.transition")
                .tag("from", from)
                .tag("to", to)
                .register(registry)
                .increment();
    }

    public void transitionConflict(String expected, String target) {
        Counter.builder("rtc.call.state.transition.conflict")
                .tag("expected", expected)
                .tag("target", target)
                .register(registry)
                .increment();
    }

    public void idempotencyHit(String operation) {
        Counter.builder("rtc.call.idempotency.hit")
                .tag("operation", operation)
                .register(registry)
                .increment();
    }

    public void reconciliation(boolean success, String trigger) {
        Counter.builder("rtc.call.reconciliation")
                .tag("result", success ? "success" : "failure")
                .tag("trigger", trigger)
                .register(registry)
                .increment();
    }

    public void timeoutLag(long lagMs, String state) {
        Timer.builder("rtc.call.timeout.lag")
                .tag("state", state)
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.ofMillis(Math.max(0L, lagMs)));
    }
}
