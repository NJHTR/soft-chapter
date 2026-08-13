package com.douyin.rtc.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * CallParticipant 状态机 — 实现 CALL_DOMAIN_MODEL.md §3。
 *
 * 说明书之外按下列语义明确补充的两条转换(服务层必须使用,随文档更新登记):
 *   JOINING   + LEAVE -> LEFT   (接通前挂断/协商阶段离开)
 *   CONNECTED + LEAVE -> LEFT   (通话中离开/挂断)
 * 其余未列出转换一律抛出 INVALID_STATE_TRANSITION。
 */
public final class ParticipantStateMachine {

    private static final Map<ParticipantState, Map<ParticipantCommand, ParticipantState>> TABLE =
            new EnumMap<>(ParticipantState.class);

    static {
        // INVITED
        add(ParticipantState.INVITED, ParticipantCommand.RING, ParticipantState.RINGING);
        add(ParticipantState.INVITED, ParticipantCommand.REJECT, ParticipantState.REJECTED);
        add(ParticipantState.INVITED, ParticipantCommand.CANCEL, ParticipantState.CANCELLED);
        // RINGING
        add(ParticipantState.RINGING, ParticipantCommand.JOIN, ParticipantState.JOINING);
        add(ParticipantState.RINGING, ParticipantCommand.REJECT, ParticipantState.REJECTED);
        add(ParticipantState.RINGING, ParticipantCommand.CANCEL, ParticipantState.CANCELLED);
        // JOINING
        add(ParticipantState.JOINING, ParticipantCommand.CONNECTED, ParticipantState.CONNECTED);
        add(ParticipantState.JOINING, ParticipantCommand.FAIL, ParticipantState.FAILED);
        add(ParticipantState.JOINING, ParticipantCommand.LEAVE, ParticipantState.LEFT);
        // CONNECTED
        add(ParticipantState.CONNECTED, ParticipantCommand.RECONNECT, ParticipantState.RECONNECTING);
        add(ParticipantState.CONNECTED, ParticipantCommand.LEAVE, ParticipantState.LEFT);
        // RECONNECTING
        add(ParticipantState.RECONNECTING, ParticipantCommand.CONNECTED, ParticipantState.CONNECTED);
        add(ParticipantState.RECONNECTING, ParticipantCommand.LEAVE, ParticipantState.LEFT);
        add(ParticipantState.RECONNECTING, ParticipantCommand.FAIL, ParticipantState.FAILED);
    }

    private ParticipantStateMachine() {
    }

    private static void add(ParticipantState from, ParticipantCommand command, ParticipantState to) {
        TABLE.computeIfAbsent(from, k -> new EnumMap<>(ParticipantCommand.class)).put(command, to);
    }

    /**
     * 校验并执行参与者状态转换。
     *
     * @throws CallDomainException 转换未列出时,code = INVALID_STATE_TRANSITION
     */
    public static ParticipantState transition(ParticipantState from, ParticipantCommand command) {
        ParticipantState to = transitionsFrom(from).get(command);
        if (to == null) {
            throw new CallDomainException(
                    CallErrorCode.INVALID_STATE_TRANSITION,
                    "非法参与者状态转换: " + from + " + " + command);
        }
        return to;
    }

    public static boolean canTransition(ParticipantState from, ParticipantCommand command) {
        return transitionsFrom(from).containsKey(command);
    }

    public static Map<ParticipantCommand, ParticipantState> transitionsFrom(ParticipantState from) {
        Map<ParticipantCommand, ParticipantState> map = TABLE.get(from);
        if (map == null) {
            return Collections.emptyMap();
        }
        return map;
    }

    public static boolean isFinal(ParticipantState state) {
        return state == ParticipantState.REJECTED
                || state == ParticipantState.CANCELLED
                || state == ParticipantState.FAILED
                || state == ParticipantState.LEFT;
    }
}