package com.douyin.kafka;

import com.douyin.entity.Message;
import com.douyin.entity.Notification;
import com.douyin.entity.User;
import com.douyin.kafka.dto.ChatMessageEvent;
import com.douyin.kafka.dto.NotificationEvent;
import com.douyin.service.MessageService;
import com.douyin.service.UserService;
import com.douyin.vo.UserVO;
import com.douyin.websocket.SessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kafka 消息消费者。
 * 从 Kafka 拉取消息 → 持久化到 DB → 通过 WebSocket 推送给在线用户。
 *
 * 三个消费者实例（与分区数对应），并发处理，互不干扰。
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "douyin.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaMessageConsumer {

    private final MessageService messageService;
    private final UserService userService;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public KafkaMessageConsumer(MessageService messageService, UserService userService,
                                SessionManager sessionManager, ObjectMapper objectMapper) {
        this.messageService = messageService;
        this.userService = userService;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    /** 消费聊天消息 — 3 个并发消费者，对应 3 个分区 */
    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_CHAT_MESSAGE,
            concurrency = "3",
            containerFactory = "kafkaListenerContainerFactory")
    public void onChatMessage(ChatMessageEvent event, Acknowledgment ack) {
        try {
            log.debug("Consuming chat: from={} to={}", event.getFromUserId(), event.getToUserId());

            // 1. 持久化到 DB
            Message msg = messageService.sendMessage(
                    event.getFromUserId(),
                    event.getToUserId(),
                    event.getContent(),
                    event.getMsgType(),
                    event.getExtra());

            // 2. 构建推送 JSON
            String json = buildChatPushJson(msg, event);

            // 3. 推送给双方在线用户
            sessionManager.pushBoth(event.getFromUserId(), event.getToUserId(), json);

            log.debug("Chat processed: msgId={}, from={} to={}", msg.getId(),
                    event.getFromUserId(), event.getToUserId());

        } catch (Exception e) {
            log.error("Chat consume failed: from={} to={}", event.getFromUserId(), event.getToUserId(), e);
        } finally {
            ack.acknowledge(); // 手动提交 offset，保证 at-least-once
        }
    }

    /** 消费互动通知 — 3 个并发消费者 */
    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_NOTIFICATION,
            concurrency = "3",
            containerFactory = "kafkaListenerContainerFactory")
    public void onNotification(NotificationEvent event, Acknowledgment ack) {
        try {
            log.debug("Consuming notify: user={} type={}", event.getUserId(), event.getType());

            // 1. 持久化通知到 DB
            Notification n = new Notification();
            n.setUserId(event.getUserId());
            n.setFromUserId(event.getFromUserId());
            n.setType(event.getType());
            n.setVideoId(event.getVideoId());
            n.setCommentId(event.getCommentId());
            n.setContent(event.getContent());
            messageService.createNotification(n);

            // 2. 推送通知给在线接收者
            if (sessionManager.isOnline(event.getUserId())) {
                String json = buildNotificationPushJson(n, event);
                sessionManager.push(event.getUserId(), json);
            }

            log.debug("Notify processed: userId={} type={}", event.getUserId(), event.getType());

        } catch (Exception e) {
            log.error("Notify consume failed: user={} type={}", event.getUserId(), event.getType(), e);
        } finally {
            ack.acknowledge();
        }
    }

    private String buildChatPushJson(Message msg, ChatMessageEvent event) throws JsonProcessingException {
        User fromUser = userService.getById(event.getFromUserId());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", msg.getId());
        resp.put("from_user_id", msg.getFromUserId());
        resp.put("to_user_id", msg.getToUserId());
        resp.put("content", msg.getContent());
        resp.put("msg_type", msg.getMsgType());
        resp.put("extra", msg.getExtra());
        resp.put("create_time", msg.getCreateTime() != null ? msg.getCreateTime().toString() : null);
        if (fromUser != null) {
            resp.put("from_user", UserVO.from(fromUser));
        }
        return objectMapper.writeValueAsString(resp);
    }

    private String buildNotificationPushJson(Notification n, NotificationEvent event) throws JsonProcessingException {
        User fromUser = event.getFromUserId() != null ? userService.getById(event.getFromUserId()) : null;
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", n.getId());
        resp.put("user_id", n.getUserId());
        resp.put("from_user_id", n.getFromUserId());
        resp.put("type", n.getType());
        resp.put("video_id", n.getVideoId());
        resp.put("content", n.getContent());
        resp.put("create_time", n.getCreateTime() != null ? n.getCreateTime().toString() : null);
        if (fromUser != null) {
            resp.put("from_user", UserVO.from(fromUser));
        }
        return objectMapper.writeValueAsString(resp);
    }
}
