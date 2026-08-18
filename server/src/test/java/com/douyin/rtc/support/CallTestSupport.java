package com.douyin.rtc.support;

import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.service.AclService;
import com.douyin.rtc.service.CallLedgerService;
import com.douyin.rtc.service.CallService;
import com.douyin.rtc.service.CreateCallCommand;

/**
 * 测试常量与便捷构造。
 */
public final class CallTestSupport {

    public static final Long INITIATOR = 1001L;
    public static final Long CALLEE = 1002L;
    public static final Long STRANGER = 9999L;
    public static final Long GROUP_ID = 5001L;
    public static final Long GROUP_MEMBER_3 = 1003L;

    private CallTestSupport() {
    }

    public static CallService service(RtcRepoFixture fx) {
        return new CallService(fx.sessions, fx.participants, fx.events,
                new AclService(fx.acl),
                new CallLedgerService(fx.events, fx.projection),
                fx.redis,
                fx.constRingingTtl(), fx.constNegotiatingTtl());
    }

    public static CallSession createDirect(CallService svc, RtcRepoFixture fx) {
        return createDirect(svc, fx, "creq-00000001", "evt-create-0001");
    }

    public static CallSession createDirect(CallService svc, RtcRepoFixture fx, String clientRequestId, String eventId) {
        fx.setMutualFollow(INITIATOR, CALLEE);
        return svc.createCall(new CreateCallCommand(
                INITIATOR, "direct", CALLEE, null, "audio", "livekit",
                clientRequestId, eventId, "trace-create"));
    }

    /** 完整走到 CONNECTED 的会话(双方都已 JOINING -> CONNECTED) */
    public static CallSession makeConnected(CallService svc, RtcRepoFixture fx) {
        CallSession call = createDirect(svc, fx);
        svc.acceptCall(call.getCallId(), CALLEE, "evt-accept-0001", "trace");
        svc.joinCall(call.getCallId(), INITIATOR, "evt-join-0001", "trace");
        svc.startNegotiation(call.getCallId(), CALLEE, "evt-neg-0001", "trace");
        return svc.confirmConnected(call.getCallId(), CALLEE, "evt-connected-0001", "trace");
    }

    public static CreateCallCommand directCommand(Long initiator, Long target, String clientRequestId, String eventId) {
        return new CreateCallCommand(initiator, "direct", target, null, "audio", "livekit",
                clientRequestId, eventId, "trace-create");
    }
}
