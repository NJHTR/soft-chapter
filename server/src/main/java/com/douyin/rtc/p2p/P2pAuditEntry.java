package com.douyin.rtc.p2p;

import java.util.List;

/**
 * P2P 审计条目(只记元数据与摘要,禁用 JWT/TURN 密码/媒体 URL/SDP/ICE 私网地址/原文)。
 */
public record P2pAuditEntry(String callId, long topologyGeneration, Long actorUserId, String kind,
                            String detail, String eventIdHash, long createdEpochMs) {

    public static P2pAuditEntry of(String callId, long topologyGeneration, Long actorUserId, String kind,
                                   String detail, String eventId, long createdEpochMs) {
        return new P2pAuditEntry(callId, topologyGeneration, actorUserId, kind, detail,
                P2pDigest.redact(eventId), createdEpochMs);
    }

    public static List<P2pAuditEntry> ofList(P2pAuditEntry first) {
        return List.of(first);
    }
}