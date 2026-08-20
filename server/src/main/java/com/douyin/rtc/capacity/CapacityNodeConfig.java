package com.douyin.rtc.capacity;

import lombok.Data;

import java.util.Map;

/**
 * 单个 LiveKit/TURN 节点的容量登记（唯一 node_id + 每维上限）。
 * 无真实集群时只作为配置模板，不能宣称容量。
 */
@Data
public class CapacityNodeConfig {
    /** 唯一节点标识（不带 region/zone 后缀，与 Redis routing 的 node id 一致） */
    private String nodeId;
    private String region = "default";
    private String zone = "default";
    /** 节点对外 advertised IP；空表示未发布（开发单节点） */
    private String advertisedIp = "";
    private String version = "";
    /** 该节点每维容量上限；0/缺失的维度不参与决策 */
    private Map<CapacityDimension, Double> limits = Map.of();
}