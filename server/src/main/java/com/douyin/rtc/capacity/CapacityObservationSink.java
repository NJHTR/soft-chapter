package com.douyin.rtc.capacity;

/**
 * 只写观测源端口：部署中的监控/采集方把每个节点的低基数容量值写入 registry。
 * 生产由 LiveKit/coturn 指标采集、控制面自身（CPU/内存）和客户端 QoE 摘要共同驱动；
 * RTC-011 只提供端口与实现，不伪造采集数据。
 */
@FunctionalInterface
public interface CapacityObservationSink {
    /**
     * @param nodeId 唯一节点标识
     * @param epoch  节点/路由绑定世代；改变时必须 rebind（重置 observed/remaining 与 seq floor）
     * @param dim    维度
     * @param value  观测值（0/负数视为不可观测，不覆盖已有值）
     */
    void record(String nodeId, long epoch, CapacityDimension dim, double value);
}