package com.douyin.rtc.stage;

/**
 * Stage 命令事件(契约 §6):每个命令使用 event_id + stage_generation 幂等/CAS。
 * eventId 由客户端生成,重放返回第一次结果;generation 单调递增,乱序旧事件被拒。
 */
public final class StageEvent {

    private final StageCommand command;
    private final long liveId;
    private final long userId;
    private final long actorUserId;
    private final String eventId;
    private final long generation;
    private final long requestedAtEpochMs;

    public StageEvent(StageCommand command, long liveId, long userId, long actorUserId,
                      String eventId, long generation, long requestedAtEpochMs) {
        this.command = command;
        this.liveId = liveId;
        this.userId = userId;
        this.actorUserId = actorUserId;
        this.eventId = eventId;
        this.generation = generation;
        this.requestedAtEpochMs = requestedAtEpochMs;
    }

    public StageCommand command() {
        return command;
    }

    public long liveId() {
        return liveId;
    }

    public long userId() {
        return userId;
    }

    public long actorUserId() {
        return actorUserId;
    }

    public String eventId() {
        return eventId;
    }

    public long generation() {
        return generation;
    }

    public long requestedAtEpochMs() {
        return requestedAtEpochMs;
    }
}