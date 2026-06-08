package com.douyin.kafka;

import com.douyin.entity.Message;
import com.douyin.entity.User;
import com.douyin.kafka.dto.ChatMessageEvent;
import com.douyin.kafka.dto.GroupMessageEvent;
import com.douyin.kafka.dto.NotificationEvent;
import com.douyin.service.GroupChatService;
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
    private final GroupChatService groupChatService;
    private final UserService userService;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public KafkaMessageConsumer(MessageService messageService, GroupChatService groupChatService,
                                UserService userService, SessionManager sessionManager,
                                ObjectMapper objectMapper) {
        this.messageService = messageService;
        this.groupChatService = groupChatService;
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

    /** 消费互动通知 — 3 个并发消费者（仅负责 WebSocket 实时推送，DB 落库由调用方保证） */
    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_NOTIFICATION,
            concurrency = "3",
            containerFactory = "kafkaListenerContainerFactory")
    public void onNotification(NotificationEvent event, Acknowledgment ack) {
        try {
            log.info("[KAFKA-CONSUME] notify received: toUser={} fromUser={} type={} content={}",
                    event.getUserId(), event.getFromUserId(), event.getType(), event.getContent());

            boolean online = sessionManager.isOnline(event.getUserId());
            log.info("[KAFKA-CONSUME] target user online={} userId={}", online, event.getUserId());

            if (online) {
                String json = buildNotificationPushJson(event);
                sessionManager.push(event.getUserId(), json);
                log.info("[KAFKA-CONSUME] WS pushed to userId={} jsonLen={}", event.getUserId(), json.length());
            }

        } catch (Exception e) {
            log.error("[KAFKA-CONSUME] notify consume FAILED: toUser={} type={} error={}",
                    event.getUserId(), event.getType(), e.getMessage(), e);
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

    /** 消费群聊消息 — 3 个并发消费者 */
    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_GROUP_MESSAGE,
            concurrency = "3",
            containerFactory = "kafkaListenerContainerFactory")
    public void onGroupMessage(GroupMessageEvent event, Acknowledgment ack) {
        try {
            log.debug("Consuming group chat: group={} from={}", event.getGroupId(), event.getFromUserId());

            com.douyin.entity.GroupMessage msg = groupChatService.sendGroupMessage(
                    event.getGroupId(),
                    event.getFromUserId(),
                    event.getContent(),
                    event.getMsgType(),
                    event.getExtra());

            String json = buildGroupPushJson(msg, event);

            java.util.List<Long> memberUids = groupChatService.getGroupMembers(event.getGroupId())
                    .stream().map(com.douyin.vo.GroupMemberVO::getUserId).toList();
            // 推送给所有群成员（含发送者，前端有 dedup 保护）
            sessionManager.pushToGroupMembers(memberUids, null, json);

            log.debug("Group chat processed: msgId={}, group={} from={}", msg.getId(),
                    event.getGroupId(), event.getFromUserId());

        } catch (Exception e) {
            log.error("Group chat consume failed: group={} from={}", event.getGroupId(), event.getFromUserId(), e);
        } finally {
            ack.acknowledge();
        }
    }

    private String buildGroupPushJson(com.douyin.entity.GroupMessage msg, GroupMessageEvent event) throws JsonProcessingException {
        User fromUser = userService.getById(event.getFromUserId());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", msg.getId());
        resp.put("group_id", msg.getGroupId());
        resp.put("from_user_id", msg.getFromUserId());
        resp.put("content", msg.getContent());
        resp.put("msg_type", msg.getMsgType());
        resp.put("extra", msg.getExtra());
        resp.put("create_time", msg.getCreateTime() != null ? msg.getCreateTime().toString() : null);
        if (fromUser != null) {
            resp.put("from_user", UserVO.from(fromUser));
        }
        resp.put("type", "group_message");
        return objectMapper.writeValueAsString(resp);
    }

    private String buildNotificationPushJson(NotificationEvent event) throws JsonProcessingException {
        User fromUser = event.getFromUserId() != null ? userService.getById(event.getFromUserId()) : null;
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("user_id", event.getUserId());
        resp.put("from_user_id", event.getFromUserId());
        resp.put("type", event.getType());
        resp.put("video_id", event.getVideoId());
        resp.put("content", event.getContent());
        if (fromUser != null) {
            resp.put("from_user", UserVO.from(fromUser));
        }
        return objectMapper.writeValueAsString(resp);
    }
}
