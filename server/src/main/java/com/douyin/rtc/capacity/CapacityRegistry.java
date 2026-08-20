package com.douyin.rtc.capacity;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 节点观测 registry。每维记录 observed 值与采样时间；epoch 变化即 rebind，
 * 重置 observed 与 seq floor，防止旧节点世代数据被当作当前容量。
 */
public class CapacityRegistry {

    private final Object lock = new Object();
    private final Map<String, NodeState> nodes = new java.util.concurrent.ConcurrentHashMap<>();
    private final long staleAfterMillis;

    public CapacityRegistry(long staleAfterMillis) {
        this.staleAfterMillis = Math.max(1_000L, staleAfterMillis);
    }

    public void record(String nodeId, long epoch, CapacityDimension dim, double value) {
        if (nodeId == null || nodeId.isBlank() || dim == null || !(value > 0)) return;
        NodeState state = nodes.computeIfAbsent(nodeId.trim(), ignored -> new NodeState());
        synchronized (state.lock) {
            if (state.epoch != epoch) {
                state.epoch = epoch;
                state.observed.clear();
                state.seqFloor = 0;
            }
            state.observed.put(dim, value);
            state.lastSeen = Instant.now();
        }
    }

    /** 返回指定节点的新鲜快照；stale 或未知节点返回 stale 快照供调用方 fail-closed。 */
    public CapacitySnapshot snapshot(String nodeId, long epoch) {
        NodeState state = nodes.get(nodeId);
        if (state == null) return CapacitySnapshot.stale(nodeId, epoch);
        synchronized (state.lock) {
            if (state.epoch != epoch) return CapacitySnapshot.stale(nodeId, epoch);
            if (Instant.now().isAfter(state.lastSeen.plusMillis(staleAfterMillis))) {
                return CapacitySnapshot.stale(nodeId, epoch);
            }
            return new CapacitySnapshot(nodeId, state.epoch, state.lastSeen,
                    Collections.unmodifiableMap(new EnumMap<>(state.observed)));
        }
    }

    public long seqFloor(String nodeId, long epoch) {
        NodeState state = nodes.get(nodeId);
        if (state == null || state.epoch != epoch) return 0;
        synchronized (state.lock) {
            return state.seqFloor;
        }
    }

    /** 当前被观测的世代；未知节点返回 -1（调用方应自行 fail-closed）。 */
    public long epoch(String nodeId) {
        NodeState state = nodes.get(nodeId);
        if (state == null) return -1;
        synchronized (state.lock) {
            return state.epoch;
        }
    }

    /** 把已消费的 reservation 吸收进该节点已观测用量，同时推进 seq floor。 */
    public void absorbObserved(String nodeId, long epoch, long seq, Map<CapacityDimension, Double> deltas) {
        NodeState state = nodes.computeIfAbsent(nodeId, ignored -> new NodeState());
        synchronized (state.lock) {
            if (state.epoch != epoch) {
                // rebind：新世代从零开始，不允许旧世代凭据吸收。
                state.epoch = epoch;
                state.observed.clear();
                state.seqFloor = seq;
            } else {
                if (seq <= state.seqFloor) return; // 乱序/重复吸收直接丢弃
                state.seqFloor = seq;
            }
            for (Map.Entry<CapacityDimension, Double> entry : deltas.entrySet()) {
                double next = state.observed.getOrDefault(entry.getKey(), 0.0) + entry.getValue();
                state.observed.put(entry.getKey(), Math.max(0.0, next));
            }
        }
    }

    private static final class NodeState {
        final Object lock = new Object();
        long epoch = -1;
        long seqFloor = 0;
        Instant lastSeen = Instant.now();
        final EnumMap<CapacityDimension, Double> observed = new EnumMap<>(CapacityDimension.class);
    }
}