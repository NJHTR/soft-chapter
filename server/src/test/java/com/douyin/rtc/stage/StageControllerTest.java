package com.douyin.rtc.stage;

import com.douyin.common.Result;
import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.controller.StageController;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * StageController 契约测试(纯 JUnit5 + Mockito,不启动 Spring)。
 */
class StageControllerTest {

    private static final long HOST = 100L;

    private StageService svc;
    private StageController controller;
    private HttpServletRequest req;

    @BeforeEach
    void setUp() {
        StageStore store = new InMemoryStageStore();
        StageProperties props = new StageProperties();
        props.setHostUserIds(List.of(HOST));
        svc = new StageService(store, mock(StageProviderPort.class), props);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.getUserIdFromToken(anyString())).thenAnswer(inv ->
                Long.parseLong(inv.getArgument(0).toString().replace("t-", "")));
        controller = new StageController(svc, jwtUtil);
        req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer t-" + HOST);
    }

    private static Map<String, Object> body(String... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void requiresLogin() {
        when(req.getHeader("Authorization")).thenReturn(null);
        assertThatThrownBy(() -> controller.request(body("live_id", "9", "event_id", "e1"), req))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.NOT_AUTHORIZED));
    }

    @Test
    void requestApproveJoinedRoundTrip() {
        when(req.getHeader("Authorization")).thenReturn("Bearer t-1");
        Result<?> r1 = controller.request(body("live_id", "9", "event_id", "e1"), req);
        assertThat(((Map<?, ?>) r1.getData()).get("status")).isEqualTo("REQUESTED");

        when(req.getHeader("Authorization")).thenReturn("Bearer t-" + HOST);
        Result<?> r2 = controller.approve(body("live_id", "9", "target_user_id", "1", "event_id", "e2"), req);
        assertThat(((Map<?, ?>) r2.getData()).get("status")).isEqualTo("PROMOTING");
        assertThat(((Map<?, ?>) r2.getData()).get("may_publish")).isEqualTo(false);

        Result<?> r3 = controller.joined(body("live_id", "9", "target_user_id", "1", "event_id", "e3"), req);
        assertThat(((Map<?, ?>) r3.getData()).get("status")).isEqualTo("ON_STAGE");
        assertThat(((Map<?, ?>) r3.getData()).get("may_publish")).isEqualTo(true);
    }

    @Test
    void nonHostApproveIsForbidden() {
        when(req.getHeader("Authorization")).thenReturn("Bearer t-2");
        controller.request(body("live_id", "9", "event_id", "e1"), req);
        assertThatThrownBy(() -> controller.approve(body("live_id", "9", "target_user_id", "2", "event_id", "e2"), req))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.NOT_AUTHORIZED));
    }

    @Test
    void missingEventIdIsInvalidArgument() {
        assertThatThrownBy(() -> controller.request(body("live_id", "9"), req))
                .isInstanceOf(CallDomainException.class)
                .satisfies(t -> assertThat(((CallDomainException) t).getCode())
                        .isEqualTo(CallErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void membersAndAuditAreReadable() {
        when(req.getHeader("Authorization")).thenReturn("Bearer t-1");
        controller.request(body("live_id", "9", "event_id", "e1"), req);
        when(req.getHeader("Authorization")).thenReturn("Bearer t-" + HOST);
        controller.approve(body("live_id", "9", "target_user_id", "1", "event_id", "e2"), req);
        Result<?> members = controller.members(9);
        assertThat((List<?>) members.getData()).hasSize(1);
        Result<?> audit = controller.audit(9);
        assertThat((List<?>) audit.getData()).hasSize(2);
    }
}