package com.douyin.rtc.stage;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Stage 状态机(契约 §6)。对未列出的转换直接抛 INVALID_STATE_TRANSITION,
 * 禁止隐式修正状态;重放/乱序由 {@link StageService} 以 event_id + generation 处理。
 */
public final class StageStateMachine {

    private static final Map<StageMemberStatus, Map<StageCommand, StageMemberStatus>> TABLE = new EnumMap<>(StageMemberStatus.class);

    static {
        put(StageMemberStatus.AUDIENCE, StageCommand.REQUEST, StageMemberStatus.REQUESTED);
        put(StageMemberStatus.REQUESTED, StageCommand.REQUEST, StageMemberStatus.REQUESTED);
        put(StageMemberStatus.REQUESTED, StageCommand.APPROVE, StageMemberStatus.PROMOTING);
        put(StageMemberStatus.REQUESTED, StageCommand.LEFT, StageMemberStatus.AUDIENCE);
        put(StageMemberStatus.REQUESTED, StageCommand.REVOKE, StageMemberStatus.REVOKING);
        put(StageMemberStatus.PROMOTING, StageCommand.JOINED, StageMemberStatus.ON_STAGE);
        put(StageMemberStatus.PROMOTING, StageCommand.LEFT, StageMemberStatus.AUDIENCE);
        put(StageMemberStatus.PROMOTING, StageCommand.REVOKE, StageMemberStatus.REVOKING);
        put(StageMemberStatus.ON_STAGE, StageCommand.DEMOTE, StageMemberStatus.DEMOTING);
        put(StageMemberStatus.ON_STAGE, StageCommand.REVOKE, StageMemberStatus.REVOKING);
        put(StageMemberStatus.DEMOTING, StageCommand.LEFT, StageMemberStatus.AUDIENCE);
        put(StageMemberStatus.DEMOTING, StageCommand.REVOKE, StageMemberStatus.REVOKING);
        put(StageMemberStatus.REVOKING, StageCommand.CONFIRM_REVOKED, StageMemberStatus.REVOKED);
        put(StageMemberStatus.DEMOTING, StageCommand.CONFIRM_LEFT, StageMemberStatus.AUDIENCE);
    }

    private static final Set<StageMemberStatus> FINAL = EnumSet.of(StageMemberStatus.REVOKED);

    private StageStateMachine() {
    }

    public static StageMemberStatus transition(StageMemberStatus from, StageCommand command) {
        StageMemberStatus to = TABLE.getOrDefault(from, Map.of()).get(command);
        if (to == null) {
            throw new CallDomainException(CallErrorCode.INVALID_STATE_TRANSITION,
                    "Stage 状态机未列出转换: " + from + " + " + command);
        }
        return to;
    }

    public static boolean isFinal(StageMemberStatus status) {
        return FINAL.contains(status);
    }

    private static void put(StageMemberStatus from, StageCommand command, StageMemberStatus to) {
        TABLE.computeIfAbsent(from, k -> new EnumMap<>(StageCommand.class)).put(command, to);
    }
}