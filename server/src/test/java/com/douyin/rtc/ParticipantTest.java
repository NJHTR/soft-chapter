package com.douyin.rtc;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.domain.CallJson;
import com.douyin.rtc.domain.CallParticipant;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.domain.CallState;
import com.douyin.rtc.service.CallService;
import com.douyin.rtc.service.CreateCallCommand;
import com.douyin.rtc.support.CallTestSupport;
import com.douyin.rtc.support.RtcRepoFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.douyin.rtc.support.CallTestSupport.CALLEE;
import static com.douyin.rtc.support.CallTestSupport.GROUP_ID;
import static com.douyin.rtc.support.CallTestSupport.GROUP_MEMBER_3;
import static com.douyin.rtc.support.CallTestSupport.INITIATOR;
import static com.douyin.rtc.support.CallTestSupport.STRANGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 参与者状态机 + ACL 拒绝路径契约。
 */
class ParticipantTest {

    private RtcRepoFixture fx;
    private CallService svc;

    @BeforeEach
    void setUp() {
        fx = new RtcRepoFixture();
        svc = CallTestSupport.service(fx);
    }

    // ==================== 参与者状态流 ====================

    @Test
    void createRingsAllParticipants() {
        CallSession call = CallTestSupport.createDirect(svc, fx);

        CallParticipant initiator = fx.participant(call.getCallId(), INITIATOR);
        CallParticipant callee = fx.participant(call.getCallId(), CALLEE);
        assertThat(initiator.getState()).isEqualTo("RINGING");
        assertThat(initiator.getRole()).isEqualTo("initiator");
        assertThat(callee.getState()).isEqualTo("RINGING");
        assertThat(callee.getRole()).isEqualTo("member");
    }

    @Test
    void acceptMovesCalleeToJoining() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        svc.acceptCall(call.getCallId(), CALLEE, "evt-accept-0001", "trace");

        CallParticipant callee = fx.participant(call.getCallId(), CALLEE);
        assertThat(callee.getState()).isEqualTo("JOINING");
        assertThat(callee.getJoinedAt()).isNotNull();
        // 发起者保持 RINGING,直到自己 join
        assertThat(fx.participant(call.getCallId(), INITIATOR).getState()).isEqualTo("RINGING");
    }

    @Test
    void joinMovesInitiatorToJoiningAfterAccepted() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        svc.acceptCall(call.getCallId(), CALLEE, "evt-accept-0001", "trace");
        svc.joinCall(call.getCallId(), INITIATOR, "evt-join-0001", "trace");

        assertThat(fx.participant(call.getCallId(), INITIATOR).getState()).isEqualTo("JOINING");
    }

    @Test
    void confirmConnectedMovesAllJoiningParticipants() {
        CallSession call = CallTestSupport.makeConnected(svc, fx);

        assertThat(fx.participant(call.getCallId(), INITIATOR).getState()).isEqualTo("CONNECTED");
        assertThat(fx.participant(call.getCallId(), CALLEE).getState()).isEqualTo("CONNECTED");
    }

    @Test
    void hangupLeavesConnectedParticipants() {
        CallSession call = CallTestSupport.makeConnected(svc, fx);
        svc.hangupCall(call.getCallId(), INITIATOR, "evt-hangup-0001", "trace");

        assertThat(fx.participant(call.getCallId(), INITIATOR).getState()).isEqualTo("LEFT");
        assertThat(fx.participant(call.getCallId(), CALLEE).getState()).isEqualTo("LEFT");
        assertThat(fx.participant(call.getCallId(), CALLEE).getLeftAt()).isNotNull();
        // CONNECTED 挂断立即落 compat 投影(callState=1 已接通,对齐旧前端语义 0/1/2),不依赖 webhook confirmEnded
        assertThat(fx.projections).hasSize(4); // create(0) + accept(1) + connected(1) + hangup(1)
        Map<String, Object> extra = CallJson.read(fx.projections.get(fx.projections.size() - 1).getExtra());
        assertThat(CallJson.intField(extra, "callState", -1)).isEqualTo(1);
        assertThat(CallJson.longField(extra, "duration", -1)).isGreaterThanOrEqualTo(0);
        assertThat(CallJson.stringField(extra, "call_id")).isEqualTo(call.getCallId());
    }

    @Test
    void rejectMovesCalleeToRejected() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        svc.rejectCall(call.getCallId(), CALLEE, "evt-reject-0001", "trace");

        CallParticipant callee = fx.participant(call.getCallId(), CALLEE);
        assertThat(callee.getState()).isEqualTo("REJECTED");
        assertThat(callee.getReason()).isEqualTo("REJECTED");
    }

    @Test
    void failMovesParticipantsOutWithLegalTransitions() {
        CallSession call = CallTestSupport.makeConnected(svc, fx);
        svc.failCall(call.getCallId(), "evt-fail-0001", com.douyin.rtc.domain.CallEndReason.PROVIDER, "trace");

        assertThat(fx.session(call.getCallId()).getState()).isEqualTo(CallState.FAILED.name());
        // §3 参与者表: CONNECTED 无 FAIL 边,接通者按 LEAVE -> LEFT 释放
        assertThat(fx.participant(call.getCallId(), INITIATOR).getState()).isEqualTo("LEFT");
        assertThat(fx.participant(call.getCallId(), CALLEE).getState()).isEqualTo("LEFT");
        assertThat(fx.participant(call.getCallId(), CALLEE).getReason()).isEqualTo("PROVIDER");
    }

    // ==================== ACL 拒绝路径 ====================

    @Test
    void directCallRequiresMutualFollow() {
        // 未建立互相关注关系
        assertThatThrownBy(() -> svc.createCall(CallTestSupport.directCommand(
                INITIATOR, CALLEE, "creq-acl-00001", "evt-create-0001")))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.NOT_AUTHORIZED));
        assertThat(fx.sessionsByCall).isEmpty();
    }

    @Test
    void directCallAllowsMutualFollowWithoutFriendRecord() {
        fx.setMutualFollow(INITIATOR, CALLEE);

        CallSession call = svc.createCall(CallTestSupport.directCommand(
                INITIATOR, CALLEE, "creq-acl-mutual-follow-0001", "evt-create-mutual-follow-0001"));

        assertThat(call.getInitiatorId()).isEqualTo(INITIATOR);
        assertThat(fx.participant(call.getCallId(), CALLEE)).isNotNull();
        assertThat(fx.sessionsByCall).hasSize(1);
    }

    @Test
    void activeParticipantIsBusyForAnotherDirectCall() {
        CallSession active = CallTestSupport.makeConnected(svc, fx);
        fx.setMutualFollow(CALLEE, GROUP_MEMBER_3);

        assertThatThrownBy(() -> svc.createCall(CallTestSupport.directCommand(
                GROUP_MEMBER_3, CALLEE, "creq-busy-target-0001", "evt-busy-target-0001")))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.BUSY));
        assertThat(fx.sessionsByCall).containsKey(active.getCallId()).hasSize(1);
    }

    @Test
    void initiatorIsBusyForAnotherDirectCall() {
        CallTestSupport.makeConnected(svc, fx);
        fx.setMutualFollow(INITIATOR, GROUP_MEMBER_3);

        assertThatThrownBy(() -> svc.createCall(CallTestSupport.directCommand(
                INITIATOR, GROUP_MEMBER_3, "creq-busy-initiator-0001", "evt-busy-initiator-0001")))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.BUSY));
        assertThat(fx.sessionsByCall).hasSize(1);
    }

    @Test
    void leftGroupParticipantCanReceiveAnotherCall() {
        CallSession group = createGroupCall();
        svc.acceptCall(group.getCallId(), CALLEE, "evt-group-accept-busy-0001", "trace");
        svc.joinCall(group.getCallId(), INITIATOR, "evt-group-join-busy-0001", "trace");
        svc.startNegotiation(group.getCallId(), INITIATOR, "evt-group-neg-busy-0001", "trace");
        svc.confirmConnected(group.getCallId(), INITIATOR, "evt-group-connected-busy-0001", "trace");
        svc.hangupCall(group.getCallId(), CALLEE, "evt-group-leave-busy-0001", "trace");

        fx.setMutualFollow(CALLEE, STRANGER);
        CallSession next = svc.createCall(CallTestSupport.directCommand(
                CALLEE, STRANGER, "creq-after-leave-0001", "evt-after-leave-0001"));
        assertThat(next).isNotNull();
        assertThat(fx.sessionsByCall).hasSize(2);
    }

    @Test
    void directCallRejectsOneWayFollow() {
        // 仅发起方关注目标方，不能发起一对一通话
        fx.setFollow(INITIATOR, CALLEE);
        assertThatThrownBy(() -> svc.createCall(CallTestSupport.directCommand(
                INITIATOR, CALLEE, "creq-acl-one-way-follow-0001", "evt-create-one-way-follow-0001")))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.NOT_AUTHORIZED));
        assertThat(fx.sessionsByCall).isEmpty();
    }

    @Test
    void selfCallIsInvalid() {
        fx.setMutualFollow(INITIATOR, INITIATOR);
        assertThatThrownBy(() -> svc.createCall(CallTestSupport.directCommand(
                INITIATOR, INITIATOR, "creq-self-00001", "evt-create-0001")))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void unauthenticatedCreateIsRejected() {
        CreateCallCommand anonymous = new CreateCallCommand(
                null, "direct", CALLEE, null, "audio", "livekit",
                "creq-anon-0001", "evt-create-0001", "trace");

        assertThatThrownBy(() -> svc.createCall(anonymous))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.NOT_AUTHORIZED));
    }

    @Test
    void groupCallRequiresMembership() {
        assertThatThrownBy(() -> svc.createCall(new CreateCallCommand(
                INITIATOR, "group", null, GROUP_ID, "audio", "livekit",
                "creq-group1-0001", "evt-create-0001", "trace")))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.NOT_AUTHORIZED));
    }

    @Test
    void groupCallRosterComesFromServerSnapshot() {
        fx.addGroupMember(GROUP_ID, INITIATOR);
        fx.addGroupMember(GROUP_ID, CALLEE);
        fx.addGroupMember(GROUP_ID, GROUP_MEMBER_3);

        CallSession call = svc.createCall(new CreateCallCommand(
                INITIATOR, "group", null, GROUP_ID, "video", "livekit",
                "creq-group2-0001", "evt-create-0001", "trace"));

        List<CallParticipant> roster = fx.participantsOf(call.getCallId());
        assertThat(roster).hasSize(3);
        assertThat(roster).extracting(CallParticipant::getUserId)
                .containsExactlyInAnyOrder(INITIATOR, CALLEE, GROUP_MEMBER_3);
        assertThat(roster).allSatisfy(p -> assertThat(p.getRole()).isIn("initiator", "member"));
        assertThat(roster).allSatisfy(p -> assertThat(p.getProfileSnapshot()).isNotBlank());
    }

    @Test
    void groupRosterRejectsMoreThanEightParticipants() {
        for (long userId = 1001; userId <= 1009; userId++) {
            fx.addGroupMember(GROUP_ID, userId);
        }

        assertThatThrownBy(() -> svc.createCall(new CreateCallCommand(
                INITIATOR, "group", null, GROUP_ID, "video", "livekit",
                "creq-group-over-0001", "evt-create-over-0001", "trace")))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.INVALID_ARGUMENT));
        assertThat(fx.sessionsByCall).isEmpty();
    }

    @Test
    void groupAcceptsMultipleMembersWithoutRepeatingSessionTransition() {
        CallSession call = createGroupCall();

        svc.acceptCall(call.getCallId(), CALLEE, "evt-group-accept-0001", "trace");
        svc.acceptCall(call.getCallId(), GROUP_MEMBER_3, "evt-group-accept-0002", "trace");

        assertThat(fx.session(call.getCallId()).getState()).isEqualTo(CallState.ACCEPTED.name());
        assertThat(fx.participant(call.getCallId(), CALLEE).getState()).isEqualTo("JOINING");
        assertThat(fx.participant(call.getCallId(), GROUP_MEMBER_3).getState()).isEqualTo("JOINING");
        assertThat(fx.eventCount(call.getCallId(), "call.accept")).isEqualTo(2);
    }

    @Test
    void groupRejectOnlyRemovesOneMember() {
        CallSession call = createGroupCall();

        svc.rejectCall(call.getCallId(), CALLEE, "evt-group-reject-0001", "trace");

        assertThat(fx.session(call.getCallId()).getState()).isEqualTo(CallState.RINGING.name());
        assertThat(fx.participant(call.getCallId(), CALLEE).getState()).isEqualTo("REJECTED");
        assertThat(fx.participant(call.getCallId(), GROUP_MEMBER_3).getState()).isEqualTo("RINGING");

        svc.rejectCall(call.getCallId(), GROUP_MEMBER_3, "evt-group-reject-0002", "trace");
        assertThat(fx.session(call.getCallId()).getState()).isEqualTo(CallState.REJECTED.name());
        assertThat(fx.participant(call.getCallId(), INITIATOR).getState()).isEqualTo("CANCELLED");
    }

    @Test
    void groupConnectedDoesNotPromoteUnacceptedMembers() {
        CallSession call = createGroupCall();
        svc.acceptCall(call.getCallId(), CALLEE, "evt-group-accept-0011", "trace");
        svc.joinCall(call.getCallId(), INITIATOR, "evt-group-join-0011", "trace");
        svc.startNegotiation(call.getCallId(), INITIATOR, "evt-group-neg-0011", "trace");
        svc.confirmConnected(call.getCallId(), INITIATOR, "evt-group-connected-0011", "trace");

        assertThat(fx.participant(call.getCallId(), INITIATOR).getState()).isEqualTo("CONNECTED");
        assertThat(fx.participant(call.getCallId(), CALLEE).getState()).isEqualTo("CONNECTED");
        assertThat(fx.participant(call.getCallId(), GROUP_MEMBER_3).getState()).isEqualTo("RINGING");
    }

    @Test
    void groupMemberHangupLeavesOnlyThatMember() {
        CallSession call = createGroupCall();
        svc.acceptCall(call.getCallId(), CALLEE, "evt-group-accept-0021", "trace");
        svc.acceptCall(call.getCallId(), GROUP_MEMBER_3, "evt-group-accept-0022", "trace");
        svc.joinCall(call.getCallId(), INITIATOR, "evt-group-join-0021", "trace");
        svc.startNegotiation(call.getCallId(), INITIATOR, "evt-group-neg-0021", "trace");
        svc.confirmConnected(call.getCallId(), INITIATOR, "evt-group-connected-0021", "trace");

        svc.hangupCall(call.getCallId(), CALLEE, "evt-group-hangup-0021", "trace");

        assertThat(fx.session(call.getCallId()).getState()).isEqualTo(CallState.CONNECTED.name());
        assertThat(fx.participant(call.getCallId(), CALLEE).getState()).isEqualTo("LEFT");
        assertThat(fx.participant(call.getCallId(), GROUP_MEMBER_3).getState()).isEqualTo("CONNECTED");
    }

    private CallSession createGroupCall() {
        fx.addGroupMember(GROUP_ID, INITIATOR);
        fx.addGroupMember(GROUP_ID, CALLEE);
        fx.addGroupMember(GROUP_ID, GROUP_MEMBER_3);
        return svc.createCall(new CreateCallCommand(
                INITIATOR, "group", null, GROUP_ID, "video", "livekit",
                "creq-group-helper-0001", "evt-group-create-0001", "trace"));
    }

    @Test
    void calleeCannotCancel() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        assertThatThrownBy(() -> svc.cancelCall(call.getCallId(), CALLEE, "evt-cancel-0001", "trace"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.NOT_INITIATOR));
        assertThat(fx.session(call.getCallId()).getState()).isEqualTo("RINGING");
    }

    @Test
    void initiatorCannotAcceptOwnCall() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        assertThatThrownBy(() -> svc.acceptCall(call.getCallId(), INITIATOR, "evt-accept-0001", "trace"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.NOT_TARGET));
    }

    @Test
    void strangerCannotActOnCall() {
        CallSession call = CallTestSupport.createDirect(svc, fx);
        assertThatThrownBy(() -> svc.acceptCall(call.getCallId(), STRANGER, "evt-accept-0001", "trace"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.NOT_AUTHORIZED));
        assertThatThrownBy(() -> svc.hangupCall(call.getCallId(), STRANGER, "evt-hangup-0001", "trace"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.NOT_AUTHORIZED));
    }
}
