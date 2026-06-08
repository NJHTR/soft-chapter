package com.douyin.kafka;

import com.douyin.entity.Message;
import com.douyin.entity.Notification;
import com.douyin.entity.User;
import com.douyin.kafka.dto.ChatMessageEvent;
import com.douyin.kafka.dto.GroupMessageEvent;
import com.douyin.kafka.dto.NotificationEvent;
import com.douyin.service.GroupChatService;
import com.douyin.service.MessageService;
import com.douyin.service.UserService;
import com.douyin.vo.UserVO;
import com.douyin.websocket.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 直写 DB 消息发布者 — 当 douyin.kafka.enabled=false 时生效。
 * 不经过 Kafka，直接落库 + WebSocket 推送。
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "douyin.kafka.enabled", havingValue = "false")
public class DirectMessagePublisher implements MessagePublisher {

    private final MessageService messageService;
    private final GroupChatService groupChatService;
    private final UserService userService;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public DirectMessagePublisher(MessageService messageService, GroupChatService groupChatService,
                                  UserService userService, SessionManager sessionManager,
                                  ObjectMapper objectMapper) {
        this.messageService = messageService;
        this.groupChatService = groupChatService;
        this.userService = userService;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishChat(ChatMessageEvent event) {
        Message msg = messageService.sendMessage(
                event.getFromUserId(), event.getToUserId(),
                event.getContent(), event.getMsgType(), event.getExtra());

        try {
            User fromUser = userService.getById(event.getFromUserId());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("id", msg.getId());
            resp.put("from_user_id", msg.getFromUserId());
            resp.put("to_user_id", msg.getToUserId());
            resp.put("content", msg.getContent());
            resp.put("msg_type", msg.getMsgType());
            resp.put("create_time", msg.getCreateTime() != null ? msg.getCreateTime().toString() : null);
            if (fromUser != null) resp.put("from_user", UserVO.from(fromUser));
            String json = objectMapper.writeValueAsString(resp);
            sessionManager.pushBoth(event.getFromUserId(), event.getToUserId(), json);
        } catch (Exception e) {
            log.error("Direct chat push failed", e);
        }
    }

    @Override
    public void publishGroupChat(GroupMessageEvent event) {
        com.douyin.entity.GroupMessage msg = groupChatService.sendGroupMessage(
                event.getGroupId(), event.getFromUserId(),
                event.getContent(), event.getMsgType(), event.getExtra());

        try {
            User fromUser = userService.getById(event.getFromUserId());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("id", msg.getId());
            resp.put("group_id", msg.getGroupId());
            resp.put("from_user_id", msg.getFromUserId());
            resp.put("content", msg.getContent());
            resp.put("msg_type", msg.getMsgType());
            resp.put("extra", msg.getExtra());
            resp.put("create_time", msg.getCreateTime() != null ? msg.getCreateTime().toString() : null);
            if (fromUser != null) resp.put("from_user", UserVO.from(fromUser));
            resp.put("type", "group_message");
            String json = objectMapper.writeValueAsString(resp);

            java.util.List<Long> memberUids = groupChatService.getGroupMembers(event.getGroupId())
                    .stream().map(com.douyin.vo.GroupMemberVO::getUserId).toList();
            sessionManager.pushToGroupMembers(memberUids, null, json);
        } catch (Exception e) {
            log.error("Direct group chat push failed", e);
        }
    }

    @Override
    public void publishNotification(NotificationEvent event) {
        Notification n = new Notification();
        n.setUserId(event.getUserId());
        n.setFromUserId(event.getFromUserId());
        n.setType(event.getType());
        n.setVideoId(event.getVideoId());
        n.setCommentId(event.getCommentId());
        n.setContent(event.getContent());
        messageService.createNotification(n);

        if (sessionManager.isOnline(event.getUserId())) {
            try {
                User fromUser = userService.getById(event.getFromUserId());
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("id", n.getId());
                resp.put("user_id", n.getUserId());
                resp.put("from_user_id", n.getFromUserId());
                resp.put("type", n.getType());
                resp.put("content", n.getContent());
                resp.put("create_time", n.getCreateTime() != null ? n.getCreateTime().toString() : null);
                if (fromUser != null) resp.put("from_user", UserVO.from(fromUser));
                sessionManager.push(event.getUserId(), objectMapper.writeValueAsString(resp));
            } catch (Exception e) {
                log.error("Direct notify push failed", e);
            }
        }
    }
}
