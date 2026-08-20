package com.douyin.rtc.p2p;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单实例内存 state store(状态转移与 consent 均以 CAS/计算守卫保护,重复 event_id 幂等)。
 * 多实例拓扑需 Redis 化,见 RTC-015 not_run 记录。
 */
public class InMemoryP2pStateStore implements P2pStateStore {

    private final Map<String, String> statuses = new ConcurrentHashMap<>();
    private final Map<String, P2pConsentState> consents = new ConcurrentHashMap<>();
    private final Map<String, String> fallbackReasons = new ConcurrentHashMap<>();

    @Override
    public String getStatus(String callId) {
        return statuses.get(callId);
    }

    @Override
    public String getFallbackReason(String callId) {
        return fallbackReasons.get(callId);
    }

    @Override
    public boolean casStatus(String callId, String expected, String newStatus, String fallbackReason) {
        boolean ok;
        String current = statuses.get(callId);
        if (expected == null) {
            ok = current == null && statuses.putIfAbsent(callId, newStatus) == null;
        } else {
            if (!expected.equals(current)) {
                return false;
            }
            String prev = statuses.put(callId, newStatus);
            ok = expected.equals(prev == null ? null : prev);
        }
        if (ok && fallbackReason != null) {
            fallbackReasons.put(callId, fallbackReason);
        }
        return ok;
    }

    @Override
    public P2pConsentState upsertConsent(String callId, long topologyGeneration, String userId, String eventId, long expiryEpochMs) {
        P2pConsentState state = consents.computeIfAbsent(callId,
                k -> P2pConsentState.none(callId, topologyGeneration, System.currentTimeMillis()));
        state.getConsents().compute(userId, (k, existing) -> {
            if (existing != null && existing.isGranted() && eventId.equals(existing.getEventId())) {
                return existing;
            }
            P2pConsentState.ConsentEntry entry = new P2pConsentState.ConsentEntry();
            entry.setUserId(userId);
            entry.setTopologyGeneration(topologyGeneration);
            entry.setEventId(eventId);
            entry.setGranted(true);
            entry.setExpiryEpochMs(expiryEpochMs);
            return entry;
        });
        state.setUpdatedEpochMs(System.currentTimeMillis());
        return state;
    }

    @Override
    public P2pConsentState revokeConsent(String callId, long topologyGeneration, String userId) {
        P2pConsentState state = consents.get(callId);
        if (state == null) {
            return null;
        }
        P2pConsentState.ConsentEntry gone = state.getConsents().remove(userId);
        if (gone != null) {
            state.setUpdatedEpochMs(System.currentTimeMillis());
        }
        return state;
    }

    @Override
    public P2pConsentState getConsent(String callId) {
        return consents.get(callId);
    }

    @Override
    public void remove(String callId) {
        statuses.remove(callId);
        consents.remove(callId);
        fallbackReasons.remove(callId);
    }
}