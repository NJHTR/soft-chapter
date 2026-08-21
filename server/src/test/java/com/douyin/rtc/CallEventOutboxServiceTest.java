package com.douyin.rtc;

import com.douyin.kafka.KafkaTopicConfig;
import com.douyin.kafka.entity.EventOutbox;
import com.douyin.kafka.mapper.EventOutboxMapper;
import com.douyin.rtc.domain.CallEvent;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.service.CallEventOutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CallEventOutboxServiceTest {

    @Test
    void callIdIsPartitionKeyAndVersionsArePersisted() {
        EventOutboxMapper mapper = mock(EventOutboxMapper.class);
        CallEventOutboxService service = new CallEventOutboxService(
                mapper, new ObjectMapper().findAndRegisterModules());
        CallSession call = new CallSession();
        call.setCallId("call-42");
        call.setState("ACCEPTED");
        call.setStateVersion(2L);
        CallEvent event = new CallEvent();
        event.setEventId("event-42");
        event.setCallId("call-42");
        event.setKind("call.accept");
        event.setEventVersion(2L);
        event.setOccurredAt(LocalDateTime.now());

        service.enqueue(call, event);

        org.mockito.ArgumentCaptor<EventOutbox> row =
                org.mockito.ArgumentCaptor.forClass(EventOutbox.class);
        verify(mapper).insert((EventOutbox) row.capture());
        assertThat(row.getValue().getTopic()).isEqualTo(KafkaTopicConfig.TOPIC_RTC_CALL_EVENTS);
        assertThat(row.getValue().getEventKey()).isEqualTo("call-42");
        assertThat(row.getValue().getEventId()).isEqualTo("event-42");
        assertThat(row.getValue().getPayload())
                .contains("\"aggregate_id\":\"call-42\"")
                .contains("\"event_version\":2")
                .contains("\"state_version\":2");
    }

    @Test
    void outboxFailureIsNotSwallowed() {
        EventOutboxMapper mapper = mock(EventOutboxMapper.class);
        org.mockito.Mockito.when(mapper.insert(org.mockito.ArgumentMatchers.<EventOutbox>any()))
                .thenThrow(new RuntimeException("db down"));
        CallEventOutboxService service = new CallEventOutboxService(
                mapper, new ObjectMapper().findAndRegisterModules());
        CallSession call = new CallSession();
        call.setCallId("call-42");
        CallEvent event = new CallEvent();
        event.setEventId("event-42");
        event.setOccurredAt(LocalDateTime.now());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.enqueue(call, event))
                .isInstanceOf(IllegalStateException.class);
    }
}
