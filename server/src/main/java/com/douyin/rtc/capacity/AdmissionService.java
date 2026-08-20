package com.douyin.rtc.capacity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 容量 admission。只对 新房/首次媒体 token 做决策，已有房间不迁移不结束。
 *
 * <p>决策语义（rtc-scale-control-contract.md 第 2 节）：
 * <ul>
 *   <li>任一关键维度 ratio ≥ hardThreshold(0.8) → REJECT_NEW_ROOM</li>
 *   <li>70% 持续 5 分钟 → 告警（n/a：持续窗口由监控 rules 承担），当前为
 *       DEGRADE_VIDEO 提示</li>
 *   <li>其余 → ALLOW</li>
 *   <li>snapshot 缺失/stale、store 不可用且无新鲜缓存 → fail-closed
 *       REJECT_NEW_ROOM</li>
 * </ul>
 *
 * <p>reservation 生命周期用 CAS 收敛；节点/epoch 改变必须 rebind，旧世代数据
 * 不得被推算为当前容量。
 */
public class AdmissionService {

    private final CapacityProperties properties;
    private final CapacityRegistry registry;
    private final CapacityReservationStore reservations;

    public AdmissionService(CapacityProperties properties,
                            CapacityRegistry registry,
                            CapacityReservationStore reservations) {
        this.properties = properties;
        this.registry = registry;
        this.reservations = reservations;
    }

    public AdmissionDecision evaluate(String nodeId, long epoch, String callId, String requestId) {
        if (!properties.isAdmissionEnabled()) {
            return AdmissionDecision.allow(nodeId, epoch, AdmissionReason.DISABLED);
        }
        CapacityNodeConfig node = node(nodeId);
        if (node == null) {
            return AdmissionDecision.reject(nodeId, epoch, AdmissionReason.UNKNOWN_NODE);
        }
        CapacitySnapshot snapshot = registry.snapshot(nodeId, epoch);
        if (snapshot.sampledAt().equals(Instant.EPOCH)) {
            return AdmissionDecision.reject(nodeId, epoch, AdmissionReason.STALE_OR_MISSING_SNAPSHOT);
        }
        CapacityReservation existing = getReservation(requestId, callId);
        Map<CapacityDimension, Double> reserved = existing == null
                ? Map.of() : existing.reserved();

        boolean degrade = false;
        for (Map.Entry<CapacityDimension, Double> entry : node.getLimits().entrySet()) {
            CapacityDimension dim = entry.getKey();
            Double limit = entry.getValue();
            if (limit == null || limit <= 0) continue;
            double total = snapshot.observed().getOrDefault(dim, 0.0)
                    + reserved.getOrDefault(dim, 0.0);
            double ratio = total / limit;
            if (ratio >= properties.getHardThreshold()) {
                return AdmissionDecision.reject(nodeId, epoch, AdmissionReason.overCapacity(dim));
            }
            if (ratio >= properties.getWarnThreshold()) {
                degrade = true;
            }
        }
        return degrade
                ? AdmissionDecision.degrade(nodeId, epoch)
                : AdmissionDecision.allow(nodeId, epoch, AdmissionReason.OK);
    }

    /**
     * PENDING 幂等创建（同 requestId 返回原记录）。不升级为 CONSUMED。
     * store 不可用抛 {@link CapacityReservationStore.CapacityStoreUnavailable}。
     */
    public CapacityReservation reserve(String requestId, String callId, String nodeId, long epoch,
                                       Map<CapacityDimension, Double> vector,
                                       Instant now) {
        CapacityReservation reservation = new CapacityReservation(
                requestId, callId, nodeId, epoch, CapacityReservation.Status.PENDING,
                Map.copyOf(vector), 0L, now.plusMillis(properties.getReservationTtlMillis()), now);
        return reservations.createIfAbsent(reservation);
    }

    /** PENDING -> CONSUMED；重复 consume 幂等返回已 CONSUMED 记录。 */
    public CapacityReservation consume(String requestId) {
        CapacityReservation current = reservations.get(requestId);
        if (current == null) return null;
        if (current.status() == CapacityReservation.Status.CONSUMED) return current;
        CapacityReservation moved = reservations.cas(requestId, CapacityReservation.Status.PENDING,
                CapacityReservation.Status.CONSUMED);
        if (moved == null) return reservations.get(requestId);
        return moved;
    }

    /**
     * CONSUMED -> ABSORBED：把预留向量按 seq 吸收进节点 observed，
     * 推进 seq floor；旧/乱序 seq、node/epoch 改变按契约 rebind 或丢弃。
     */
    public CapacityReservation absorb(String requestId, long seq) {
        CapacityReservation current = reservations.get(requestId);
        if (current == null) return null;
        if (current.status() == CapacityReservation.Status.ABSORBED) return current;
        CapacityReservation moved = reservations.cas(requestId, CapacityReservation.Status.CONSUMED,
                CapacityReservation.Status.ABSORBED);
        if (moved == null) {
            CapacityReservation raced = reservations.get(requestId);
            if (raced != null && raced.status() == CapacityReservation.Status.ABSORBED) {
                registry.absorbObserved(raced.nodeId(), raced.epoch(), seq, raced.reserved());
            }
            return raced;
        }
        registry.absorbObserved(moved.nodeId(), moved.epoch(), seq, moved.reserved());
        return moved;
    }

    /** CONSUMED/PENDING -> RELEASED（异常回滚时不吸收）。 */
    public CapacityReservation release(String requestId) {
        CapacityReservation current = reservations.get(requestId);
        if (current == null) return null;
        if (current.status() == CapacityReservation.Status.RELEASED) return current;
        CapacityReservation moved = reservations.cas(requestId, current.status(),
                CapacityReservation.Status.RELEASED);
        if (moved != null) return moved;
        return reservations.get(requestId);
    }

    public int expirePending(Instant now) {
        return reservations.expirePending(now);
    }

    public CapacityRegistry registry() {
        return registry;
    }

    public CapacityProperties properties() {
        return properties;
    }

    private CapacityNodeConfig node(String nodeId) {
        for (CapacityNodeConfig node : properties.getNodes()) {
            if (node.getNodeId() != null && node.getNodeId().equals(nodeId)) return node;
        }
        return null;
    }

    private CapacityReservation getReservation(String requestId, String callId) {
        try {
            CapacityReservation byRequest = requestId == null || requestId.isBlank()
                    ? null : reservations.get(requestId);
            if (byRequest != null) return byRequest;
            // 尚无 reservation 时按 call 查活跃记录（PENDING 对应既有房间重放）。
            for (CapacityReservation reservation : reservations.listActive()) {
                if (callId != null && callId.equals(reservation.callId())) return reservation;
            }
            return null;
        } catch (CapacityReservationStore.CapacityStoreUnavailable e) {
            throw e;
        }
    }
}