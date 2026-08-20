package com.douyin.rtc.p2p;

/**
 * P2P 信令/consent 状态存储抽象。当前实现为单实例内存存储;
 * 多实例 topologies 需迁移到 Redis(未执行,记录 not_run)。
 */
public interface P2pStateStore {

    /** 读取当前拓扑状态,不存在返回 null */
    String getStatus(String callId);

    /** 最近一次写入的 FALLING_BACK 原因,未回退时返回 null */
    String getFallbackReason(String callId);

    /** 条件更新:仅当 currentStatus 匹配 expected 时写入 newStatus,返回是否成功 */
    boolean casStatus(String callId, String expected, String newStatus, String fallbackReason);

    /**
     * 记录某方 consent。replay:该 user 已有相同 event_id 的 granted 条目时
     * 原样返回(不延长 TTL);否则新建条目。返回更新后的聚合。
     */
    P2pConsentState upsertConsent(String callId, long topologyGeneration, String userId, String eventId, long expiryEpochMs);

    /** 撤销某方 consent(删条目),返回更新后的聚合 */
    P2pConsentState revokeConsent(String callId, long topologyGeneration, String userId);

    P2pConsentState getConsent(String callId);

    void remove(String callId);
}