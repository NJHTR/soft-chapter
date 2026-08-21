package com.douyin.kafka.reliability;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * 消费端可靠性：手动 ack + 指数退避重试 + 超限进 DLQ。
 *
 * <p>覆盖 Boot 默认的 kafkaListenerContainerFactory：
 * <ul>
 *   <li>{@code AckMode.MANUAL}：只有业务处理成功才提交 offset；</li>
 *   <li>{@code DefaultErrorHandler}：处理抛异常时按指数退避同分区重投，最多
 *       {@code consumer-max-attempts} 次；</li>
 *   <li>{@code DeadLetterPublishingRecoverer}：重试耗尽后写入 {@code <topic>-dlq}
 *       并计数 kafka.dlq，避免无限重试击穿 Broker（总任务 §22）。</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(value = "douyin.kafka.enabled", havingValue = "true")
@EnableConfigurationProperties(KafkaReliabilityProperties.class)
public class KafkaReliabilityConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaReliabilityProperties properties,
            KafkaReliabilityMetrics metrics) {

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + properties.getDltSuffix(), record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(
                properties.getConsumerBackoffInitialMs(),
                properties.getConsumerBackoffMultiplier());
        backOff.setMaxInterval(properties.getConsumerBackoffMaxMs());
        backOff.setMaxElapsedTime(retryBudgetMs(properties));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (ConsumerRecord<?, ?> record, Exception ex) -> {
                    metrics.dlq(record.topic());
                    logDlt(record, ex);
                    dlt.accept(record, ex);
                },
                backOff);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    /**
     * consumer-max-attempts=N 对应 N-1 次重试的退避总预算（ms）。
     * 默认 4 次尝试 -> 1000+2000+4000=7000ms 后仍失败则进 DLQ。
     */
    private long retryBudgetMs(KafkaReliabilityProperties properties) {
        int retries = Math.max(0, properties.getConsumerMaxAttempts() - 1);
        double mul = Math.max(1.0, properties.getConsumerBackoffMultiplier());
        long budget = 0;
        for (int i = 0; i < retries; i++) {
            budget += (long) (properties.getConsumerBackoffInitialMs() * Math.pow(mul, i));
        }
        return budget;
    }

    private void logDlt(ConsumerRecord<?, ?> record, Exception ex) {
        org.slf4j.LoggerFactory.getLogger(KafkaReliabilityConfig.class)
                .error("[KAFKA-RELIABILITY] message exhausted retries -> DLQ topic={} partition={} offset={} error={}",
                        record.topic(), record.partition(), record.offset(), ex.getMessage());
    }
}
