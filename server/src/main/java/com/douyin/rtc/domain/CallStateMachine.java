package com.douyin.rtc.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * CallSession 状态机 — 严格实现 CALL_DOMAIN_MODEL.md §2 转移表。
 *
 * 表中未列出的转换一律抛出 {@link CallDomainException}(INVALID_STATE_TRANSITION),
 * 禁止隐式修正状态。终态(REJECTED/CANCELLED/EXPIRED/FAILED/ENDED)不接受任何命令;
 * 终态命令的幂等重放语义由服务层处理(返回当前状态,不创建新记录)。
 */
public final class CallStateMachine {

    private static final Map<CallState, Map<CallCommand, CallState>> TABLE =
            new EnumMap<>(CallState.class);

    static {
        // CREATED
        add(CallState.CREATED, CallCommand.CREATE, CallState.RINGING);
        add(CallState.CREATED, CallCommand.FAIL, CallState.FAILED);
        // RINGING
        add(CallState.RINGING, CallCommand.ACCEPT, CallState.ACCEPTED);
        add(CallState.RINGING, CallCommand.REJECT, CallState.REJECTED);
        add(CallState.RINGING, CallCommand.CANCEL, CallState.CANCELLED);
        add(CallState.RINGING, CallCommand.EXPIRE, CallState.EXPIRED);
        // ACCEPTED
        add(CallState.ACCEPTED, CallCommand.NEGOTIATE_START, CallState.NEGOTIATING);
        add(CallState.ACCEPTED, CallCommand.HANGUP, CallState.ENDED);
        add(CallState.ACCEPTED, CallCommand.FAIL, CallState.FAILED);
        // NEGOTIATING
        add(CallState.NEGOTIATING, CallCommand.CONNECTED, CallState.CONNECTED);
        add(CallState.NEGOTIATING, CallCommand.HANGUP, CallState.ENDED);
        add(CallState.NEGOTIATING, CallCommand.FAIL, CallState.FAILED);
        // CONNECTED
        add(CallState.CONNECTED, CallCommand.HANGUP, CallState.ENDING);
        add(CallState.CONNECTED, CallCommand.FAIL, CallState.FAILED);
        // ENDING
        add(CallState.ENDING, CallCommand.ENDED, CallState.ENDED);
    }

    private CallStateMachine() {
    }

    private static void add(CallState from, CallCommand command, CallState to) {
        TABLE.computeIfAbsent(from, k -> new EnumMap<>(CallCommand.class)).put(command, to);
    }

    /**
     * 校验并执行状态转换。
     *
     * @throws CallDomainException 转换未在转移表中列出时,code = INVALID_STATE_TRANSITION
     */
    public static CallState transition(CallState from, CallCommand command) {
        CallState to = transitionsFrom(from).get(command);
        if (to == null) {
            throw new CallDomainException(
                    CallErrorCode.INVALID_STATE_TRANSITION,
                    "非法状态转换: " + from + " + " + command);
        }
        return to;
    }

    public static boolean canTransition(CallState from, CallCommand command) {
        return transitionsFrom(from).containsKey(command);
    }

    /** 当前状态允许的命令集合(只读)。 */
    public static Map<CallCommand, CallState> transitionsFrom(CallState from) {
        Map<CallCommand, CallState> map = TABLE.get(from);
        if (map == null) {
            return Collections.emptyMap();
        }
        return map;
    }

    public static boolean isFinal(CallState state) {
        return state == CallState.REJECTED
                || state == CallState.CANCELLED
                || state == CallState.EXPIRED
                || state == CallState.FAILED
                || state == CallState.ENDED;
    }
}
