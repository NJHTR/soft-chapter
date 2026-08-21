package com.douyin.kafka.reliability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka 可靠性指标（低基数计数器）：
 * outbox 投递/重试/死信/直发回退、消费去重命中、DLQ 写入、账本降级。
 * 无 registry 时 Micrometer 默认 SimpleMeterRegistry，不丢数据。
 */
@Component
public class KafkaReliabilityMetrics {

    private final MeterRegistry registry;

    public KafkaReliabilityMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void outboxDispatched(String topic) {
        counter("kafka.outbox.dispatched", topic).increment();
    }

    public void outboxRetry(String topic) {
        counter("kafka.outbox.retry", topic).increment();
    }

    public void outboxDead(String topic) {
        counter("kafka.outbox.dead", topic).increment();
    }

    public void outboxEnqueueFailure(String topic) {
        counter("kafka.outbox.enqueue.failure", topic).increment();
    }

    public void ledgerDuplicate(String topic) {
        counter("kafka.ledger.duplicate", topic).increment();
    }

    public void ledgerFailOpen(String topic) {
        counter("kafka.ledger.failopen", topic).increment();
    }

    public void dlq(String topic) {
        counter("kafka.dlq", topic).increment();
    }

    public void lagProbeError() {
        counter("kafka.lag.probe.error", "").increment();
    }

    public void gaugePending(long value) {
        registry.gauge("kafka.outbox.pending", value);
    }

    public void gaugeConsumerLag(String group, String topic, int partition, long lag) {
        registry.gauge("kafka.consumer.lag",
                List.of(Tag.of("group", group), Tag.of("topic", topic), Tag.of("partition", String.valueOf(partition))),
                lag);
    }

    private Counter counter(String name, String topic) {
        return Counter.builder(name)
                .tag("topic", topic == null || topic.isEmpty() ? "none" : topic)
                .register(registry);
    }
}
