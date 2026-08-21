package com.douyin.kafka.reliability;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Consumer lag 采样（总任务 §21/§43：kafka_consumer_lag 可观测）。
 * 通过 AdminClient 对消费组各分区采样 high watermark 与已提交 offset 的差值，
 * 以 gauge 写入 Micrometer。30s 一次，采样失败只记指标不阻断业务。
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "douyin.kafka.enabled", havingValue = "true")
public class KafkaLagProbe {

    private final KafkaProperties kafkaProperties;
    private final KafkaReliabilityProperties properties;
    private final KafkaReliabilityMetrics metrics;

    public KafkaLagProbe(KafkaProperties kafkaProperties,
                         KafkaReliabilityProperties properties,
                         KafkaReliabilityMetrics metrics) {
        this.kafkaProperties = kafkaProperties;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${douyin.kafka.reliability.lag-probe-interval-ms:30000}",
            initialDelayString = "30000")
    public void probe() {
        if (!properties.isLagProbeEnabled()) {
            return;
        }
        Map<String, Object> adminProps = new HashMap<>();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(adminProps)) {
            String groupId = kafkaProperties.getConsumer().getGroupId();
            if (groupId == null) {
                return;
            }
            Map<TopicPartition, OffsetAndMetadata> committed =
                    admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata()
                            .get(5, TimeUnit.SECONDS);
            if (committed == null || committed.isEmpty()) {
                return;
            }
            Map<TopicPartition, OffsetSpec> latestRequests = new HashMap<>();
            committed.keySet().forEach(tp -> latestRequests.put(tp, OffsetSpec.latest()));
            Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> latest =
                    admin.listOffsets(latestRequests).all().get(5, TimeUnit.SECONDS);

            committed.forEach((tp, meta) -> {
                var info = latest.get(tp);
                if (info != null && meta != null && meta.offset() >= 0) {
                    long lag = Math.max(0L, info.offset() - meta.offset());
                    metrics.gaugeConsumerLag(groupId, tp.topic(), tp.partition(), lag);
                    if (lag > 0) {
                        log.debug("[KAFKA-LAG] group={} topic={} partition={} lag={}",
                                groupId, tp.topic(), tp.partition(), lag);
                    }
                }
            });
        } catch (Exception e) {
            metrics.lagProbeError();
            log.warn("[KAFKA-LAG] probe failed: {}", e.getMessage());
        }
    }
}
