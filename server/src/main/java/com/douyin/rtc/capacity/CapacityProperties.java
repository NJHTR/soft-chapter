package com.douyin.rtc.capacity;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 容量配置（rtc.capacity.*）。默认关闭硬决策；开启时只有新房/首次媒体
 * token 会受影响，已有房间保持不动。阈值固定语义：70% 持续 5 分钟告警并
 * 计划扩容，80% 为紧急硬门禁（见 rtc-scale-control-contract.md 第 2 节）。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rtc.capacity")
public class CapacityProperties {

    /** 硬决策开关，默认 false；false 时 evaluate 直接返回 ALLOW(disabled) */
    private boolean admissionEnabled = false;

    /** 节点容量观测快照新鲜度（毫秒），超时视为 stale 并 fail-closed */
    private long snapshotStaleAfterMillis = 15_000L;

    /** 新房/媒体 reservation TTL（毫秒），PENDING 到期后按 EXPIRED 收敛 */
    private long reservationTtlMillis = 60_000L;

    /** 扩容告警阈值（0.7） */
    private double warnThreshold = 0.7;

    /** 紧急硬门禁阈值（0.8） */
    private double hardThreshold = 0.8;

    /** 阈值用于 ratio = observed / limit；限制为 0 的维度视为不可用 */
    private List<CapacityNodeConfig> nodes = new ArrayList<>();

    /** 生产使用 Redis 持久化 reservation；本机单实例或测试使用内存实现 */
    private boolean redisReservationEnabled = false;
}