package com.douyin.rtc.service;

import com.douyin.kafka.KafkaTopicConfig;
import com.douyin.kafka.entity.EventOutbox;
import com.douyin.kafka.mapper.EventOutboxMapper;
import com.douyin.rtc.domain.CallEvent;
import com.douyin.rtc.domain.CallSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Writes call events to the shared outbox inside the CallService database transaction. */
@Service
@ConditionalOnProperty(value = "douyin.kafka.enabled", havingValue = "true")
public class CallEventOutboxService {

    private final EventOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public CallEventOutboxService(EventOutboxMapper outboxMapper, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    public void enqueue(CallSession call, CallEvent event) {
        if (call == null || event == null) {
            return;
        }
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("schema", "douyin.realtime.v1");
            envelope.put("event_id", event.getEventId());
            envelope.put("aggregate_id", call.getCallId());
            envelope.put("event_version", event.getEventVersion());
            envelope.put("state_version", call.getStateVersion());
            envelope.put("kind", event.getKind());
            envelope.put("occurred_at", event.getOccurredAt());
            envelope.put("call", call);
            envelope.put("payload", event.getPayload());

            EventOutbox row = new EventOutbox();
            row.setTopic(KafkaTopicConfig.TOPIC_RTC_CALL_EVENTS);
            row.setEventKey(call.getCallId());
            row.setEventId(event.getEventId());
            row.setPayload(objectMapper.writeValueAsString(envelope));
            row.setStatus(EventOutbox.STATUS_PENDING);
            row.setRetryCount(0);
            row.setNextAttemptAt(LocalDateTime.now());
            outboxMapper.insert(row);
        } catch (Exception e) {
            throw new IllegalStateException("RTC call outbox enqueue failed", e);
        }
    }
}
