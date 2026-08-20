package com.douyin.rtc.p2p;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 双方 consent 聚合:绑定 call_id + topology_generation,
 * 双方各持一个 consent 条目(带独立 TTL),双方均同意且未过期才算 CONSENTED。
 */
@Data
public class P2pConsentState {

    private String callId;

    private long topologyGeneration;

    private long updatedEpochMs;

    /** key = 用户 id(字符串),value = 该方 consent 条目 */
    private Map<String, ConsentEntry> consents = new ConcurrentHashMap<>();

    public static P2pConsentState none(String callId, long topologyGeneration, long nowEpochMs) {
        P2pConsentState state = new P2pConsentState();
        state.setCallId(callId);
        state.setTopologyGeneration(topologyGeneration);
        state.setUpdatedEpochMs(nowEpochMs);
        return state;
    }

    public boolean hasConsented(String userId) {
        ConsentEntry entry = consents.get(String.valueOf(userId));
        return entry != null && entry.isGranted();
    }

    /** 双方均已同意(不管是否过期),用于协议流程判定 */
    public boolean bothGranted() {
        return consents.size() == 2 && consents.values().stream().allMatch(ConsentEntry::isGranted);
    }

    /** 双方均已同意且在 nowEpochMs 之前未过期 */
    public boolean bothGrantedAt(long nowEpochMs) {
        if (!bothGranted()) {
            return false;
        }
        return consents.values().stream().allMatch(e -> e.isValidAt(nowEpochMs));
    }

    /** 任一 consenter 已过期 */
    public boolean anyExpiredAt(long nowEpochMs) {
        return consents.values().stream().anyMatch(e -> !e.isValidAt(nowEpochMs));
    }

    @Data
    public static class ConsentEntry {
        private String userId;
        private long topologyGeneration;
        private String eventId;
        private boolean granted;
        private long expiryEpochMs;

        public boolean isValidAt(long nowEpochMs) {
            return granted && nowEpochMs < expiryEpochMs;
        }
    }
}