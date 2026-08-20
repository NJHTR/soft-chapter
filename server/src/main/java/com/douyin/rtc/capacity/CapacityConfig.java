package com.douyin.rtc.capacity;

import com.douyin.rtc.observability.QoeAggregateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 容量组件装配。生产启用 rtc.capacity.redis-reservation-enabled=true 使用
 * Redis store；单机开发默认内存实现。两套实现的 CAS 语义一致。
 */
@Configuration
public class CapacityConfig {

    @Bean
    public CapacityRegistry capacityRegistry(CapacityProperties properties) {
        return new CapacityRegistry(properties.getSnapshotStaleAfterMillis());
    }

    @Bean
    public CapacityObservationSink capacityObservationSink(CapacityRegistry registry) {
        return registry::record;
    }

    @Bean
    @ConditionalOnProperty(name = "rtc.capacity.redis-reservation-enabled", havingValue = "true")
    public CapacityReservationStore redisCapacityReservationStore(StringRedisTemplate redis,
                                                                  ObjectMapper mapper,
                                                                  CapacityProperties properties) {
        return new RedisCapacityReservationStore(redis, mapper,
                properties.getReservationTtlMillis());
    }

    @Bean
    @ConditionalOnProperty(name = "rtc.capacity.redis-reservation-enabled", havingValue = "false",
            matchIfMissing = true)
    public CapacityReservationStore inMemoryCapacityReservationStore() {
        return new InMemoryCapacityReservationStore();
    }

    @Bean
    public AdmissionService admissionService(CapacityProperties properties,
                                             CapacityRegistry registry,
                                             CapacityReservationStore store) {
        return new AdmissionService(properties, registry, store);
    }

    @Bean
    public QoeAggregateStore qoeAggregateStore(
            @Value("${rtc.qoe.retention:15m}") Duration retention) {
        return new QoeAggregateStore(retention);
    }
}