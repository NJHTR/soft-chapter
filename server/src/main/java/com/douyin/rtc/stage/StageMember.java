package com.douyin.rtc.stage;

import java.util.Objects;

/**
 * Stage 成员快照。generation/lastEventId 用于 CAS 与重放判重;
 * providerPending 表示 DEMOTING/REVOKING 的 provider 发布权限移除尚未收敛。
 */
public final class StageMember {

    private final long liveId;
    private final long userId;
    private final StageMemberStatus status;
    private final long generation;
    private final String lastEventId;
    private final boolean providerPending;
    private final long updatedAtEpochMs;

    public StageMember(long liveId, long userId, StageMemberStatus status, long generation,
                       String lastEventId, boolean providerPending, long updatedAtEpochMs) {
        this.liveId = liveId;
        this.userId = userId;
        this.status = status;
        this.generation = generation;
        this.lastEventId = lastEventId;
        this.providerPending = providerPending;
        this.updatedAtEpochMs = updatedAtEpochMs;
    }

    /** AUDIENCE + gen0 的空成员,用于首个命令前不存在于 store 的成员。 */
    public static StageMember audience(long liveId, long userId) {
        return new StageMember(liveId, userId, StageMemberStatus.AUDIENCE, 0, null, false, 0L);
    }

    public StageMember with(StageMemberStatus newStatus, long newGeneration, String eventId,
                            boolean newProviderPending, long now) {
        return new StageMember(liveId, userId, newStatus, newGeneration, eventId,
                newProviderPending, now);
    }

    public long liveId() {
        return liveId;
    }

    public long userId() {
        return userId;
    }

    public StageMemberStatus status() {
        return status;
    }

    public long generation() {
        return generation;
    }

    public String lastEventId() {
        return lastEventId;
    }

    public boolean providerPending() {
        return providerPending;
    }

    public long updatedAtEpochMs() {
        return updatedAtEpochMs;
    }

    /** 该成员当前是否允许获得 LiveKit publish token(仅 ON_STAGE)。 */
    public boolean mayPublish() {
        return status == StageMemberStatus.ON_STAGE;
    }

    public String key() {
        return liveId + ":" + userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StageMember other)) {
            return false;
        }
        return liveId == other.liveId && userId == other.userId && generation == other.generation
                && status == other.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(liveId, userId, status, generation);
    }

    @Override
    public String toString() {
        return "StageMember{" + key() + "," + status + ",gen=" + generation + ",pending=" + providerPending + "}";
    }
}