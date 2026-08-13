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
    void directCallRequiresMutualFriendship() {
        // 未建立好友关系
        assertThatThrownBy(() -> svc.createCall(CallTestSupport.directCommand(
                INITIATOR, CALLEE, "creq-acl-00001", "evt-create-0001")))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.NOT_AUTHORIZED));
        assertThat(fx.sessionsByCall).isEmpty();
    }

    @Test
    void selfCallIsInvalid() {
        fx.setMutualFriend(INITIATOR, INITIATOR);
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