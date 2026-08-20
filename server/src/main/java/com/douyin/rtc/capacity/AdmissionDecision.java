package com.douyin.rtc.capacity;

/**
 * Admission 决策结果。决策枚举不允许静默迁移或结束已有房间；
 * reason 使用固定低基数枚举（含维度级原因）。
 */
public record AdmissionDecision(
        Decision decision,
        String nodeId,
        long epoch,
        AdmissionReason reason
) {
    public enum Decision {
        ALLOW,
        DEGRADE_VIDEO,
        REJECT_NEW_ROOM
    }

    public static AdmissionDecision allow(String nodeId, long epoch, AdmissionReason reason) {
        return new AdmissionDecision(Decision.ALLOW, nodeId, epoch, reason);
    }

    public static AdmissionDecision degrade(String nodeId, long epoch) {
        return new AdmissionDecision(Decision.DEGRADE_VIDEO, nodeId, epoch,
                AdmissionReason.WARN_THRESHOLD);
    }

    public static AdmissionDecision reject(String nodeId, long epoch, AdmissionReason reason) {
        return new AdmissionDecision(Decision.REJECT_NEW_ROOM, nodeId, epoch, reason);
    }

    public boolean allowed() {
        return decision == Decision.ALLOW;
    }
}