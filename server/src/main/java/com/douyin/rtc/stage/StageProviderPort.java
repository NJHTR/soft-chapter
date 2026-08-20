package com.douyin.rtc.stage;

/**
 * Stage 的 provider 控制端口(媒体控制面)。
 * 契约 §6:DEMOTING/REVOKING 必须真实移除 LiveKit publish permission,失败则断开
 * participant;Stage 到 SRS 只经 LiveKit Egress provider 控制命令,媒体不经过 Spring。
 */
public interface StageProviderPort {

    /**
     * 移除成员在 LiveKit 房间的 publish permission(participant update)。
     *
     * @return true 表示 provider 已确认权限移除/断开;false 表示调用失败、需重试或断开后确认。
     */
    boolean revokePublishPermission(StageMember member);

    /**
     * 启动 Stage -> SRS 的 Egress(StartRoomCompositeEgress)。
     *
     * @return 关联标识(如 egress id 或 output URL),供审计与 generation 追踪。
     * @throws StageProviderException provider 不可用或拒绝时抛出
     */
    String requestStageEgress(String liveId, String roomName);
}