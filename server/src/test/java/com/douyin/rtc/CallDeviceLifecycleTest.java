package com.douyin.rtc;

import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.support.CallTestSupport;
import com.douyin.rtc.support.RtcRepoFixture;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static com.douyin.rtc.support.CallTestSupport.CALLEE;

class CallDeviceLifecycleTest {
    @Test
    void devicesAcceptIndependentlyAndSecondDeviceKeepsRinging() {
        RtcRepoFixture fx = new RtcRepoFixture();
        var service = CallTestSupport.deviceAwareService(fx);
        CallSession call = CallTestSupport.createDirect(service, fx);
        service.registerCallDevice(call.getCallId(), CALLEE, "browser-a");
        service.registerCallDevice(call.getCallId(), CALLEE, "browser-b");

        service.acceptCall(call.getCallId(), CALLEE, "evt-device-a", "trace", "browser-a");

        assertThat(fx.devicesByKey.get(call.getCallId() + "|" + CALLEE + "|browser-a").getState()).isEqualTo("ACCEPTED");
        assertThat(fx.devicesByKey.get(call.getCallId() + "|" + CALLEE + "|browser-b").getState()).isEqualTo("RINGING");
        assertThat(fx.session(call.getCallId()).getState()).isEqualTo("ACCEPTED");
    }

    @Test
    void rejectingOneDeviceDoesNotEndCallWhileAnotherRings() {
        RtcRepoFixture fx = new RtcRepoFixture();
        var service = CallTestSupport.deviceAwareService(fx);
        CallSession call = CallTestSupport.createDirect(service, fx);
        service.registerCallDevice(call.getCallId(), CALLEE, "browser-a");
        service.registerCallDevice(call.getCallId(), CALLEE, "browser-b");

        service.rejectCall(call.getCallId(), CALLEE, "evt-device-reject-a", "trace", "browser-a");

        assertThat(fx.devicesByKey.get(call.getCallId() + "|" + CALLEE + "|browser-a").getState()).isEqualTo("REJECTED");
        assertThat(fx.devicesByKey.get(call.getCallId() + "|" + CALLEE + "|browser-b").getState()).isEqualTo("RINGING");
        assertThat(fx.session(call.getCallId()).getState()).isEqualTo("RINGING");
    }

    @Test
    void terminalTimeoutCancelsEveryStillRingingDevice() {
        RtcRepoFixture fx = new RtcRepoFixture();
        var service = CallTestSupport.deviceAwareService(fx);
        CallSession call = CallTestSupport.createDirect(service, fx);
        service.registerCallDevice(call.getCallId(), CALLEE, "browser-a");
        service.registerCallDevice(call.getCallId(), CALLEE, "browser-b");
        call.setExpiresAt(LocalDateTime.now().minusSeconds(1));

        service.expireCall(call.getCallId(), "sys:ttl:device-expire", "ttl-worker");

        assertThat(fx.session(call.getCallId()).getState()).isEqualTo("EXPIRED");
        assertThat(fx.devicesByKey.values().stream()
                .filter(device -> call.getCallId().equals(device.getCallId()))
                .allMatch(device -> "CANCELLED".equals(device.getState())))
                .isTrue();
    }

    @Test
    void lateAcceptDoesNotCreateADeviceAfterTerminalTimeout() {
        RtcRepoFixture fx = new RtcRepoFixture();
        var service = CallTestSupport.deviceAwareService(fx);
        CallSession call = CallTestSupport.createDirect(service, fx);
        call.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        service.expireCall(call.getCallId(), "sys:ttl:late-device", "ttl-worker");

        service.acceptCall(call.getCallId(), CALLEE, "evt-late-device", "trace", "late-browser");

        assertThat(fx.devicesByKey.keySet())
                .noneMatch(key -> key.endsWith("|late-browser"));
    }
}
