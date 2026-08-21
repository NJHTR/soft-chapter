package com.douyin.kafka.reliability;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka 可靠性配置（douyin.kafka.reliability.*）。
 * 覆盖：outbox 调度、消费端重试退避/DLQ、账本保留期、Lag 采样。
 */
@Data
@ConfigurationProperties(prefix = "douyin.kafka.reliability")
public class KafkaReliabilityProperties {

    /** 生产者是否先写 outbox（true=事务 outbox，false=直发 Kafka） */
    private boolean outboxEnabled = true;

    /** outbox 调度轮询间隔 ms */
    private long outboxPollMs = 1000L;

    /** 每次认领批量上限 */
    private int outboxBatchSize = 100;

    /** outbox 最大投递尝试次数（超过后标记 DEAD） */
    private int outboxMaxAttempts = 8;

    /** outbox 失败退避基础间隔 ms（2^retry 指数放大，封顶 outboxBackoffMaxMs） */
    private long outboxBackoffBaseMs = 1000L;

    /** outbox 失败退避上限 ms */
    private long outboxBackoffMaxMs = 60000L;

    /** outbox PROCESSING 卡死恢复阈值 ms（实例崩溃后重新投递） */
    private long outboxStaleProcessingMs = 300000L;

    /** 消费端最大尝试次数（1 + 重试次数，超过后进 DLQ） */
    private int consumerMaxAttempts = 4;

    /** 消费端重试退避初始间隔 ms */
    private long consumerBackoffInitialMs = 1000L;

    /** 消费端重试退避倍数（指数退避） */
    private double consumerBackoffMultiplier = 2.0;

    /** 消费端重试退避上限 ms */
    private long consumerBackoffMaxMs = 60000L;

    /** 消费幂等账本保留小时数（超期清理） */
    private long ledgerRetentionHours = 24L;

    /** outbox 终态行保留小时数（超期清理） */
    private long outboxRetentionHours = 72L;

    /** 账本与 outbox 终态行清理调度间隔 ms */
    private long purgeIntervalMs = 3600000L;

    /** DLQ topic 后缀 */
    private String dltSuffix = "-dlq";

    /** 是否启用 consumer lag 采样 */
    private boolean lagProbeEnabled = true;

    /** consumer lag 采样间隔 ms */
    private long lagProbeIntervalMs = 30000L;
}