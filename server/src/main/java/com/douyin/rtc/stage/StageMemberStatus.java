package com.douyin.rtc.stage;

/**
 * Stage 成员状态(契约 §6): AUDIENCE -> REQUESTED -> PROMOTING -> ON_STAGE -> DEMOTING -> AUDIENCE,
 * 任一受控状态可因权限撤销进入 REVOKING -> REVOKED。
 */
public enum StageMemberStatus {
    /** 普通观众(默认路径 SRS WHEP/LL-HLS/HLS/HTTP-FLV/CDN,不创建 LiveKit participant) */
    AUDIENCE,
    /** 本人已申请上麦 */
    REQUESTED,
    /** 主持人批准,已预留发布名额,等待 provider 确认入房 */
    PROMOTING,
    /** 已在台发布(仅此状态可签发 publish token) */
    ON_STAGE,
    /** 下麦中:停止补签,等待移除 provider publish permission 或断开确认 */
    DEMOTING,
    /** 撤销中:立即拒绝补签并移除 publish permission */
    REVOKING,
    /** 已撤销(终态) */
    REVOKED;

    /** 占用发布名额的状态(PROMOTING 已预留名额,ON_STAGE 已发布)。 */
    public boolean holdsPublishSlot() {
        return this == PROMOTING || this == ON_STAGE;
    }

    /** 仍需向 provider 收敛发布权限的受控状态。 */
    public boolean providerSettlementPending() {
        return this == DEMOTING || this == REVOKING;
    }
}
