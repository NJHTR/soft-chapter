package com.douyin.rtc.p2p;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * P2P 拓扑严格转移表。未列出的转移抛 INVALID_STATE_TRANSITION,
 * 禁止隐式修正状态(契约第 7 节;回退必须显式经过 FALLING_BACK)。
 */
public final class P2pStateMachine {

    private static final Map<P2pTopologyStatus, Map<P2pCommand, P2pTopologyStatus>> TABLE = new EnumMap<>(P2pTopologyStatus.class);

    private P2pStateMachine() {
    }

    static {
        add(P2pTopologyStatus.DISABLED, P2pCommand.EVALUATE, P2pTopologyStatus.ELIGIBLE);
        add(P2pTopologyStatus.ELIGIBLE, P2pCommand.CONSENT, P2pTopologyStatus.CONSENTED);
        add(P2pTopologyStatus.ELIGIBLE, P2pCommand.FALLBACK, P2pTopologyStatus.FALLING_BACK);
        add(P2pTopologyStatus.CONSENTED, P2pCommand.CONSENT, P2pTopologyStatus.CONSENTED);
        add(P2pTopologyStatus.CONSENTED, P2pCommand.REVOKE, P2pTopologyStatus.ELIGIBLE);
        add(P2pTopologyStatus.CONSENTED, P2pCommand.PROBE_START, P2pTopologyStatus.PROBING);
        add(P2pTopologyStatus.CONSENTED, P2pCommand.FALLBACK, P2pTopologyStatus.FALLING_BACK);
        add(P2pTopologyStatus.PROBING, P2pCommand.PROBE_PASS, P2pTopologyStatus.P2P_CONNECTED);
        add(P2pTopologyStatus.PROBING, P2pCommand.PROBE_FAIL, P2pTopologyStatus.FALLING_BACK);
        add(P2pTopologyStatus.PROBING, P2pCommand.REVOKE, P2pTopologyStatus.FALLING_BACK);
        add(P2pTopologyStatus.PROBING, P2pCommand.FALLBACK, P2pTopologyStatus.FALLING_BACK);
        add(P2pTopologyStatus.FALLING_BACK, P2pCommand.SFU_CONNECTED_ACK, P2pTopologyStatus.SFU_CONNECTED);
        add(P2pTopologyStatus.FALLING_BACK, P2pCommand.FAIL, P2pTopologyStatus.FAILED);
        add(P2pTopologyStatus.FALLING_BACK, P2pCommand.FALLBACK, P2pTopologyStatus.FALLING_BACK);
    }

    private static void add(P2pTopologyStatus from, P2pCommand command, P2pTopologyStatus to) {
        TABLE.computeIfAbsent(from, k -> new EnumMap<>(P2pCommand.class)).put(command, to);
    }

    public static P2pTopologyStatus transition(P2pTopologyStatus from, P2pCommand command) {
        P2pTopologyStatus target = TABLE.getOrDefault(from, Collections.emptyMap()).get(command);
        if (target == null) {
            throw new CallDomainException(CallErrorCode.INVALID_STATE_TRANSITION,
                    "P2P 状态机未列出转换: " + from + " + " + command);
        }
        return target;
    }

    public static boolean canTransition(P2pTopologyStatus from, P2pCommand command) {
        return TABLE.getOrDefault(from, Collections.emptyMap()).containsKey(command);
    }

    public static Map<P2pCommand, P2pTopologyStatus> transitionsFrom(P2pTopologyStatus from) {
        Map<P2pCommand, P2pTopologyStatus> t = TABLE.get(from);
        return t == null ? Collections.emptyMap() : Collections.unmodifiableMap(t);
    }

    public static boolean isFinal(P2pTopologyStatus status) {
        return status == P2pTopologyStatus.P2P_CONNECTED
                || status == P2pTopologyStatus.SFU_CONNECTED
                || status == P2pTopologyStatus.FAILED;
    }
}