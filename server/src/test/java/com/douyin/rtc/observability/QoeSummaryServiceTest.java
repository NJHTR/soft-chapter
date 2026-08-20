package com.douyin.rtc.observability;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.domain.CallParticipant;
import com.douyin.rtc.repository.RtcCallParticipantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RTC-011 QoE 摘要服务契约：仅通话成员可上报/读取；只接受已知聚合键且
 * 数量受限；媒体明细不进入本服务（本测试不模拟任何 RTP/Kafka 通道）。
 */
class QoeSummaryServiceTest {

    private static final String CALL = "call-1";
    private static final long MEMBER = 1001L;
    private static final long STRANGER = 9999L;

    private RtcCallParticipantMapper mapper;
    private QoeAggregateStore store;
    private QoeSummaryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(RtcCallParticipantMapper.class);
        store = new QoeAggregateStore(Duration.ofMinutes(15));
        service = new QoeSummaryService(mapper, store, new CapacityMetrics(null));
        when(mapper.findByCallAndUser(CALL, MEMBER)).thenReturn(participant(MEMBER));
        when(mapper.findByCallAndUser(CALL, STRANGER)).thenReturn(null);
        when(mapper.listByCall(CALL)).thenReturn(List.of(participant(MEMBER), participant(1002L)));
    }

    private static RtcQoeSummary summary(Map<String, Double> aggregation) {
        return new RtcQoeSummary("trace-1", CALL, List.of("1001", "1002"), "node-a", "group",
                Instant.now(), aggregation);
    }

    private static CallParticipant participant(Long userId) {
        CallParticipant participant = new CallParticipant();
        participant.setCallId(CALL);
        participant.setUserId(userId);
        participant.setRole("member");
        return participant;
    }

    @Test
    void reportRejectsNonMember() {
        RtcQoeSummary summary = summary(Map.of(RtcQoeSummary.AVG_RTT_MS, 42.0));

        assertThatThrownBy(() -> service.report(summary, STRANGER))
                .isInstanceOf(CallDomainException.class)
                .extracting(e -> ((CallDomainException) e).getCode())
                .isEqualTo(CallErrorCode.NOT_AUTHORIZED);
    }

    @Test
    void reportRejectsMissingCallId() {
        RtcQoeSummary bad = new RtcQoeSummary("trace-1", null, List.of(), "node-a", "group",
                Instant.now(), Map.of(RtcQoeSummary.AVG_RTT_MS, 1.0));

        assertThatThrownBy(() -> service.report(bad, MEMBER))
                .isInstanceOf(CallDomainException.class)
                .extracting(e -> ((CallDomainException) e).getCode())
                .isEqualTo(CallErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void reportRejectsUnknownAggregationKey() {
        RtcQoeSummary summary = summary(Map.of("ghost_key", 1.0));

        assertThatThrownBy(() -> service.report(summary, MEMBER))
                .isInstanceOf(CallDomainException.class)
                .extracting(e -> ((CallDomainException) e).getCode())
                .isEqualTo(CallErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void reportRejectsNonFiniteOrNegativeValue() {
        RtcQoeSummary bad = summary(Map.of(RtcQoeSummary.AVG_RTT_MS, -3.0));

        assertThatThrownBy(() -> service.report(bad, MEMBER))
                .isInstanceOf(CallDomainException.class)
                .extracting(e -> ((CallDomainException) e).getCode())
                .isEqualTo(CallErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void reportAcceptsValidSummaryAndAggregates() {
        Map<String, Double> aggregation = new HashMap<>();
        aggregation.put(RtcQoeSummary.AVG_RTT_MS, 40.0);
        aggregation.put(RtcQoeSummary.LOSS_RATIO, 0.01);
        aggregation.put(RtcQoeSummary.RECONNECTS, 1.0);

        QoeAggregateStore.CallQoe accepted = service.report(summary(aggregation), MEMBER);

        assertThat(accepted).isNotNull();
        assertThat(accepted.samples()).isEqualTo(1);
        assertThat(accepted.participantCount()).isEqualTo(2);
        assertThat(accepted.avg(RtcQoeSummary.AVG_RTT_MS)).isEqualTo(40.0);
        assertThat(accepted.avg(RtcQoeSummary.RECONNECTS)).isEqualTo(1.0);
    }

    @Test
    void summaryForRequiresMembership() {
        assertThatThrownBy(() -> service.summaryFor(STRANGER, CALL))
                .isInstanceOf(CallDomainException.class)
                .extracting(e -> ((CallDomainException) e).getCode())
                .isEqualTo(CallErrorCode.NOT_AUTHORIZED);
    }

    @Test
    void summaryForWithoutDataReportsUnavailable() {
        assertThat(service.summaryFor(MEMBER, CALL)).containsEntry("available", false);
    }

    @Test
    void summaryForReturnsAggregatesForMember() {
        service.report(summary(Map.of(RtcQoeSummary.AVG_RTT_MS, 55.0,
                RtcQoeSummary.FPS_AVG, 30.0)), MEMBER);

        Map<String, Object> out = service.summaryFor(MEMBER, CALL);

        assertThat(out).containsEntry("available", true);
        assertThat(out).containsEntry("rtt_ms_avg", 55.0);
        assertThat(out).containsEntry("fps_avg", 30.0);
        assertThat(out).containsKey("participant_count");
    }
}