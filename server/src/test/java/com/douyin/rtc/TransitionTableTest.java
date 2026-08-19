package com.douyin.rtc;

import com.douyin.rtc.domain.CallCommand;
import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.domain.CallState;
import com.douyin.rtc.domain.CallStateMachine;
import com.douyin.rtc.domain.ParticipantCommand;
import com.douyin.rtc.domain.ParticipantState;
import com.douyin.rtc.domain.ParticipantStateMachine;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 契约测试: CALL_DOMAIN_MODEL.md §2/§3 转移表全覆盖。
 * 每条合法转换必须落目标状态;任何未列出的组合必须抛 INVALID_STATE_TRANSITION。
 */
class TransitionTableTest {

    // ==================== CallState 状态机 ====================

    private static final Map<CallState, Map<CallCommand, CallState>> LEGAL_CALL =
            Map.of(
                    CallState.CREATED, Map.of(
                            CallCommand.CREATE, CallState.RINGING,
                            CallCommand.FAIL, CallState.FAILED),
                    CallState.RINGING, Map.of(
                            CallCommand.ACCEPT, CallState.ACCEPTED,
                            CallCommand.REJECT, CallState.REJECTED,
                            CallCommand.CANCEL, CallState.CANCELLED,
                            CallCommand.EXPIRE, CallState.EXPIRED),
                    CallState.ACCEPTED, Map.of(
                            CallCommand.NEGOTIATE_START, CallState.NEGOTIATING,
                            CallCommand.HANGUP, CallState.ENDED,
                            CallCommand.FAIL, CallState.FAILED),
                    CallState.NEGOTIATING, Map.of(
                            CallCommand.CONNECTED, CallState.CONNECTED,
                            CallCommand.HANGUP, CallState.ENDED,
                            CallCommand.FAIL, CallState.FAILED),
                    CallState.CONNECTED, Map.of(
                            CallCommand.HANGUP, CallState.ENDING,
                            CallCommand.FAIL, CallState.FAILED),
                    CallState.ENDING, Map.of(
                            CallCommand.ENDED, CallState.ENDED));

    @Test
    void callStateMachineAppliesEveryLegalTransition() {
        for (CallState from : LEGAL_CALL.keySet()) {
            for (Map.Entry<CallCommand, CallState> e : LEGAL_CALL.get(from).entrySet()) {
                assertThat(CallStateMachine.canTransition(from, e.getKey()))
                        .as("%s + %s 可用", from, e.getKey())
                        .isTrue();
                assertThat(CallStateMachine.transition(from, e.getKey()))
                        .as("%s + %s 的目标状态", from, e.getKey())
                        .isEqualTo(e.getValue());
            }
        }
    }

    @Test
    void callStateMachineRejectsEveryUnlistedTransition() {
        for (CallState from : CallState.values()) {
            for (CallCommand command : CallCommand.values()) {
                if (LEGAL_CALL.containsKey(from) && LEGAL_CALL.get(from).containsKey(command)) {
                    continue;
                }
                assertThatThrownBy(() -> CallStateMachine.transition(from, command))
                        .as("非法转换必须拒绝: %s + %s", from, command)
                        .isInstanceOf(CallDomainException.class)
                        .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                                .isEqualTo(CallErrorCode.INVALID_STATE_TRANSITION));
            }
        }
    }

    @Test
    void callStateMachineTerminalStatesRejectAllCommands() {
        for (CallState terminal : CallState.values()) {
            if (!CallStateMachine.isFinal(terminal)) {
                continue;
            }
            assertThat(CallStateMachine.transitionsFrom(terminal)).isEmpty();
            assertThatThrownBy(() -> CallStateMachine.transition(terminal, CallCommand.FAIL))
                    .isInstanceOf(CallDomainException.class);
        }
    }

    @Test
    void terminalDetectionMatchesModel() {
        assertThat(CallStateMachine.isFinal(CallState.REJECTED)).isTrue();
        assertThat(CallStateMachine.isFinal(CallState.CANCELLED)).isTrue();
        assertThat(CallStateMachine.isFinal(CallState.EXPIRED)).isTrue();
        assertThat(CallStateMachine.isFinal(CallState.FAILED)).isTrue();
        assertThat(CallStateMachine.isFinal(CallState.ENDED)).isTrue();
        for (CallState nonTerminal : new CallState[]{CallState.CREATED, CallState.RINGING,
                CallState.ACCEPTED, CallState.NEGOTIATING, CallState.CONNECTED, CallState.ENDING}) {
            assertThat(CallStateMachine.isFinal(nonTerminal)).isFalse();
        }
    }

    // ==================== ParticipantState 状态机 ====================

    private static final Map<ParticipantState, Map<ParticipantCommand, ParticipantState>> LEGAL_PARTICIPANT =
            Map.of(
                    ParticipantState.INVITED, Map.of(
                            ParticipantCommand.RING, ParticipantState.RINGING,
                            ParticipantCommand.REJECT, ParticipantState.REJECTED,
                            ParticipantCommand.CANCEL, ParticipantState.CANCELLED),
                    ParticipantState.RINGING, Map.of(
                            ParticipantCommand.JOIN, ParticipantState.JOINING,
                            ParticipantCommand.REJECT, ParticipantState.REJECTED,
                            ParticipantCommand.CANCEL, ParticipantState.CANCELLED),
                    ParticipantState.JOINING, Map.of(
                            ParticipantCommand.CONNECTED, ParticipantState.CONNECTED,
                            ParticipantCommand.FAIL, ParticipantState.FAILED,
                            ParticipantCommand.LEAVE, ParticipantState.LEFT),
                    ParticipantState.CONNECTED, Map.of(
                            ParticipantCommand.RECONNECT, ParticipantState.RECONNECTING,
                            ParticipantCommand.LEAVE, ParticipantState.LEFT),
                    ParticipantState.RECONNECTING, Map.of(
                            ParticipantCommand.CONNECTED, ParticipantState.CONNECTED,
                            ParticipantCommand.LEAVE, ParticipantState.LEFT,
                            ParticipantCommand.FAIL, ParticipantState.FAILED));

    @Test
    void participantStateMachineAppliesEveryLegalTransition() {
        for (ParticipantState from : LEGAL_PARTICIPANT.keySet()) {
            for (Map.Entry<ParticipantCommand, ParticipantState> e : LEGAL_PARTICIPANT.get(from).entrySet()) {
                assertThat(ParticipantStateMachine.canTransition(from, e.getKey()))
                        .as("%s + %s 可用", from, e.getKey())
                        .isTrue();
                assertThat(ParticipantStateMachine.transition(from, e.getKey()))
                        .as("%s + %s 的目标状态", from, e.getKey())
                        .isEqualTo(e.getValue());
            }
        }
    }

    @Test
    void participantStateMachineRejectsEveryUnlistedTransition() {
        for (ParticipantState from : ParticipantState.values()) {
            for (ParticipantCommand command : ParticipantCommand.values()) {
                if (LEGAL_PARTICIPANT.containsKey(from) && LEGAL_PARTICIPANT.get(from).containsKey(command)) {
                    continue;
                }
                assertThatThrownBy(() -> ParticipantStateMachine.transition(from, command))
                        .as("非法参与者转换必须拒绝: %s + %s", from, command)
                        .isInstanceOf(CallDomainException.class)
                        .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                                .isEqualTo(CallErrorCode.INVALID_STATE_TRANSITION));
            }
        }
    }

    @Test
    void participantTerminalStatesRejectAllCommands() {
        for (ParticipantState terminal : ParticipantState.values()) {
            if (!ParticipantStateMachine.isFinal(terminal)) {
                continue;
            }
            assertThat(ParticipantStateMachine.transitionsFrom(terminal)).isEmpty();
            assertThatThrownBy(() -> ParticipantStateMachine.transition(terminal, ParticipantCommand.FAIL))
                    .isInstanceOf(CallDomainException.class);
        }
    }
}
