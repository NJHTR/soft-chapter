package com.douyin.kafka.reliability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KafkaReliabilityConfigTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void factory_usesManualAck_andRetryWithDlt() throws Exception {
        KafkaReliabilityConfig config = new KafkaReliabilityConfig();
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer =
                mock(ConcurrentKafkaListenerContainerFactoryConfigurer.class);
        ConsumerFactory<Object, Object> consumerFactory = mock(ConsumerFactory.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaReliabilityMetrics metrics = new KafkaReliabilityMetrics(new SimpleMeterRegistry());
        KafkaReliabilityProperties properties = new KafkaReliabilityProperties();

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                config.kafkaListenerContainerFactory(configurer, consumerFactory, kafkaTemplate, properties, metrics);

        assertEquals(ContainerProperties.AckMode.MANUAL, factory.getContainerProperties().getAckMode());

        Field field = AbstractKafkaListenerContainerFactory.class.getDeclaredField("commonErrorHandler");
        field.setAccessible(true);
        CommonErrorHandler handler = (CommonErrorHandler) field.get(factory);
        assertInstanceOf(DefaultErrorHandler.class, handler,
                "消费失败应交由 DefaultErrorHandler（指数退避 + 耗尽进 DLQ）");
        assertTrue(((DefaultErrorHandler) handler).isAckAfterHandle(),
                "成功 ack 语义不被错误处理二次覆盖");
    }
}