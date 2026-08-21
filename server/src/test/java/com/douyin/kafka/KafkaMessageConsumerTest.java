package com.douyin.kafka;

import com.douyin.entity.Message;
import com.douyin.kafka.dto.ChatMessageEvent;
import com.douyin.kafka.reliability.KafkaEventLedgerService;
import com.douyin.service.GroupChatService;
import com.douyin.service.MessageService;
import com.douyin.service.UserService;
import com.douyin.websocket.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KafkaMessageConsumerTest {

    private final MessageService messageService = mock(MessageService.class);
    private final GroupChatService groupChatService = mock(GroupChatService.class);
    private final UserService userService = mock(UserService.class);
    private final SessionManager sessionManager = mock(SessionManager.class);
    private final KafkaEventLedgerService ledger = mock(KafkaEventLedgerService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private KafkaMessageConsumer newConsumer() {
        return new KafkaMessageConsumer(messageService, groupChatService, userService,
                sessionManager, objectMapper, ledger);
    }

    private ChatMessageEvent chatEvent(String eventId) {
        ChatMessageEvent event = new ChatMessageEvent();
        event.setFromUserId(1L);
        event.setToUserId(2L);
        event.setContent("hi");
        event.setMsgType(1);
        event.setExtra(null);
        event.setTimestamp(System.currentTimeMillis());
        event.setEventId(eventId);
        return event;
    }

    @Test
    void onChatMessage_skipsAlreadyProcessed_withAck() {
        when(ledger.isProcessed(eq(KafkaTopicConfig.TOPIC_CHAT_MESSAGE), eq("e1"))).thenReturn(true);
        Acknowledgment ack = mock(Acknowledgment.class);

        newConsumer().onChatMessage(chatEvent("e1"), ack);

        verify(messageService, never()).sendMessage(any(), any(), any(), any(), any());
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void onChatMessage_persistsPushesAndAcksOnSuccess() {
        Message msg = new Message();
        msg.setId(100L);
        msg.setFromUserId(1L);
        msg.setToUserId(2L);
        msg.setContent("hi");
        when(ledger.isProcessed(eq(KafkaTopicConfig.TOPIC_CHAT_MESSAGE), eq("e1"))).thenReturn(false);
        when(messageService.sendMessage(1L, 2L, "hi", 1, null)).thenReturn(msg);

        Acknowledgment ack = mock(Acknowledgment.class);
        newConsumer().onChatMessage(chatEvent("e1"), ack);

        verify(messageService, times(1)).sendMessage(1L, 2L, "hi", 1, null);
        verify(sessionManager, times(1)).pushBoth(eq(1L), eq(2L), anyString());
        verify(ledger, times(1)).markProcessedOrThrow(eq(KafkaTopicConfig.TOPIC_CHAT_MESSAGE), eq("e1"));
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void onChatMessage_throwsOnFailure_withoutAck_orLedger() {
        when(ledger.isProcessed(eq(KafkaTopicConfig.TOPIC_CHAT_MESSAGE), eq("e1"))).thenReturn(false);
        when(messageService.sendMessage(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("db down"));

        Acknowledgment ack = mock(Acknowledgment.class);
        KafkaMessageConsumer consumer = newConsumer();

        assertThrows(KafkaConsumeException.class, () -> consumer.onChatMessage(chatEvent("e1"), ack));
        verify(ack, never()).acknowledge();
        verify(ledger, never()).markProcessedOrThrow(anyString(), anyString());
    }

    @Test
    void onChatMessage_throwsOnLedgerFailure_withoutAck() {
        Message msg = new Message();
        msg.setId(100L);
        msg.setFromUserId(1L);
        msg.setToUserId(2L);
        msg.setContent("hi");
        when(ledger.isProcessed(eq(KafkaTopicConfig.TOPIC_CHAT_MESSAGE), eq("e1"))).thenReturn(false);
        when(messageService.sendMessage(1L, 2L, "hi", 1, null)).thenReturn(msg);
        doThrow(new RuntimeException("ledger down"))
                .when(ledger).markProcessedOrThrow(KafkaTopicConfig.TOPIC_CHAT_MESSAGE, "e1");

        Acknowledgment ack = mock(Acknowledgment.class);
        KafkaMessageConsumer consumer = newConsumer();

        assertThrows(KafkaConsumeException.class, () -> consumer.onChatMessage(chatEvent("e1"), ack));
        verify(ack, never()).acknowledge();
    }
}
