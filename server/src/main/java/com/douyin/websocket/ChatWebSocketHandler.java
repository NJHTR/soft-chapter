package com.douyin.websocket;

import com.douyin.entity.Message;
import com.douyin.entity.User;
import com.douyin.kafka.MessagePublisher;
import com.douyin.kafka.dto.ChatMessageEvent;
import com.douyin.kafka.dto.GroupMessageEvent;
import com.douyin.service.MessageService;
import com.douyin.service.UserService;
import com.douyin.vo.UserVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebSocket 实时聊天处理器。
 *
 * 消息流转链路：
 * 客户端A → WS → ChatWebSocketHandler → Kafka(chat-messages)
 *   → KafkaMessageConsumer → DB(t_message) + WS推送 → 客户端B
 *
 * 通话记录直接落库 + 推送，不经过 Kafka。
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final MessagePublisher messagePublisher;
    private final SessionManager sessionManager;
    private final MessageService messageService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(MessagePublisher messagePublisher, SessionManager sessionManager,
                                MessageService messageService, UserService userService,
                                ObjectMapper objectMapper) {
        this.messagePublisher = messagePublisher;
        this.sessionManager = sessionManager;
        this.messageService = messageService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.register(userId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        Long fromUserId = (Long) session.getAttributes().get("userId");
        if (fromUserId == null) return;

        Map<String, Object> payload;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> p = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(textMessage.getPayload(), Map.class);
            payload = p;
        } catch (Exception e) {
            log.error("Invalid WebSocket message: {}", textMessage.getPayload());
            return;
        }

        // 通话信令 —— 转发给目标用户(群”，call_request 同时落库为聊天记录
        String type = (String) payload.get("type");
        if ("call_signal".equals(type)) {
            // 支持单人 (to_user_id) 和多人 (to_user_ids, 列逗号分隔)
            java.util.List<Long> targetIds = new java.util.ArrayList<>();
            if (payload.get("to_user_id") != null) {
                targetIds.add(Long.valueOf(payload.get("to_user_id").toString()));
            }
            if (payload.get("to_user_ids") != null) {
                String idsStr = payload.get("to_user_ids").toString();
                for (String id : idsStr.split(",")) {
                    String trimmed = id.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            Long uid = Long.valueOf(trimmed);
                            if (!targetIds.contains(uid)) targetIds.add(uid);
                        } catch (NumberFormatException e) {
                            log.warn("Invalid to_user_id in list: {}", trimmed);
                        }
                    }
                }
            }
            if (!targetIds.isEmpty()) {
                Map<String, Object> signal = new java.util.LinkedHashMap<>();
                signal.put("type", "call_signal");
                signal.put("from_user_id", String.valueOf(fromUserId));
                signal.put("signal_type", payload.getOrDefault("signal_type", ""));
                signal.put("data", payload.getOrDefault("data", null));
                Object groupIdObj = payload.get("call_id");
                if (groupIdObj != null) signal.put("call_id", groupIdObj.toString());
                try {
                    String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(signal);
                    for (Long targetId : targetIds) {
                        sessionManager.push(targetId, json);
                    }
                    log.debug("Call signal relayed to {} users: from={} type={} targetIds={}",
                            targetIds.size(), fromUserId, payload.get("signal_type"), targetIds);
                } catch (Exception e) {
                    log.error("Failed to relay call signal", e);
                }
            }

            // 通话结束时落库为聊天记录（call_reject / hangup）—— 支持群通话多人落库
            String signalType = (String) payload.getOrDefault("signal_type", "");
            if ("call_reject".equals(signalType) || "hangup".equals(signalType)) {
                Long recordToUser = payload.get("to_user_id") != null
                        ? Long.valueOf(payload.get("to_user_id").toString()) : null;
                java.util.List<Long> recordTargets = new java.util.ArrayList<>();
                if (recordToUser != null) recordTargets.add(recordToUser);
                // 如果是群通话，信号里会带 target_ids，给每个参与者也保存一条记录
                if (payload.get("target_ids") != null) {
                    String tidsStr = payload.get("target_ids").toString();
                    for (String id : tidsStr.split(",")) {
                        String trimmed = id.trim();
                        if (!trimmed.isEmpty()) {
                            try {
                                Long uid = Long.valueOf(trimmed);
                                if (!recordTargets.contains(uid)) recordTargets.add(uid);
                            } catch (NumberFormatException ignored) { }
                        }
                    }
                }
                for (Long tuid : recordTargets) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) payload.get("data");
                    boolean isVideo = data != null && Boolean.TRUE.equals(data.get("isVideo"));
                    boolean wasAnswered = data != null && Boolean.TRUE.equals(data.get("wasAnswered"));
                    int duration = 0;
                    if (data != null && data.get("duration") != null) {
                        duration = data.get("duration") instanceof Number
                                ? ((Number) data.get("duration")).intValue() : 0;
                    }
                    String content = isVideo ? "视频通话" : "语音通话";
                    int msgType = isVideo ? 11 : 10;
                    int callState;
                    if ("call_reject".equals(signalType)) {
                        callState = 0;
                    } else {
                        callState = wasAnswered ? 1 : 2;
                    }
                    String extra = "{\"callState\":" + callState + ",\"duration\":" + duration + "}";
                    Message msg = messageService.sendMessage(fromUserId, tuid, content, msgType, extra);
                    try {
                        User fromUser = userService.getById(fromUserId);
                        Map<String, Object> pushData = new LinkedHashMap<>();
                        pushData.put("id", msg.getId());
                        pushData.put("from_user_id", msg.getFromUserId());
                        pushData.put("to_user_id", msg.getToUserId());
                        pushData.put("content", msg.getContent());
                        pushData.put("msg_type", msg.getMsgType());
                        pushData.put("extra", msg.getExtra());
                        pushData.put("create_time", msg.getCreateTime() != null ? msg.getCreateTime().toString() : null);
                        if (fromUser != null) pushData.put("from_user", UserVO.from(fromUser));
                        sessionManager.pushBoth(fromUserId, tuid, objectMapper.writeValueAsString(pushData));
                    } catch (Exception e) {
                        log.error("Call record push failed", e);
                    }
                    log.info("Call record saved: from={} to={} type={} state={}", fromUserId, tuid, isVideo ? "video" : "audio", callState);
                }
            }
            return;
        }

        Long toUserId = payload.get("to_user_id") != null
                ? Long.valueOf(payload.get("to_user_id").toString()) : null;
        Long groupId = payload.get("group_id") != null
                ? Long.valueOf(payload.get("group_id").toString()) : null;
        String content = (String) payload.getOrDefault("content", "");
        Integer msgType = payload.get("msg_type") != null
                ? Integer.valueOf(payload.get("msg_type").toString()) : 1;
        String extra = (String) payload.getOrDefault("extra", "");

        if (groupId != null) {
            // 群聊消息 → Kafka group-messages topic
            GroupMessageEvent event = new GroupMessageEvent();
            event.setGroupId(groupId);
            event.setFromUserId(fromUserId);
            event.setContent(content);
            event.setMsgType(msgType);
            event.setExtra(extra);
            event.setTimestamp(System.currentTimeMillis());
            messagePublisher.publishGroupChat(event);
            log.debug("Group chat event published to Kafka: group={} from={}", groupId, fromUserId);
            return;
        }

        if (toUserId == null) return;

        // 发布到 Kafka，后续由消费者异步落库 + 推送
        ChatMessageEvent event = new ChatMessageEvent();
        event.setFromUserId(fromUserId);
        event.setToUserId(toUserId);
        event.setContent(content);
        event.setMsgType(msgType);
        event.setExtra(extra);
        event.setTimestamp(System.currentTimeMillis());

        messagePublisher.publishChat(event);
        log.debug("Chat event published to Kafka: from={} to={}", fromUserId, toUserId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.unregister(userId, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket error: userId={}", session.getAttributes().get("userId"), exception);
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.unregister(userId, session);
        }
    }
}
