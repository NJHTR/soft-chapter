package com.douyin.controller;

import com.douyin.common.Result;
import com.douyin.entity.Message;
import com.douyin.entity.User;
import com.douyin.service.MessageService;
import com.douyin.service.UserService;
import com.douyin.utils.JwtUtil;
import com.douyin.vo.ConversationVO;
import com.douyin.vo.MessageVO;
import com.douyin.vo.NotificationVO;
import com.douyin.vo.UserVO;
import com.douyin.websocket.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/message")
public class MessageController {

    private final MessageService messageService;
    private final JwtUtil jwtUtil;
    private final SessionManager sessionManager;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public MessageController(MessageService messageService, JwtUtil jwtUtil,
                             SessionManager sessionManager, UserService userService,
                             ObjectMapper objectMapper) {
        this.messageService = messageService;
        this.jwtUtil = jwtUtil;
        this.sessionManager = sessionManager;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    private Long getLoginUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try { return jwtUtil.getUserIdFromToken(auth.substring(7)); } catch (Exception ignored) {}
        }
        return null;
    }

    /** 发送私聊消息 */
    @PostMapping("/send")
    public Result<Message> send(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");

        Long toUserId = body.get("to_user_id") != null ? Long.valueOf(body.get("to_user_id").toString()) : null;
        if (toUserId == null) return Result.fail("接收者ID不能为空");

        String content = (String) body.getOrDefault("content", "");
        Integer msgType = body.get("msg_type") != null ? Integer.valueOf(body.get("msg_type").toString()) : 1;
        String extra = (String) body.getOrDefault("extra", "");

        Message msg = messageService.sendMessage(userId, toUserId, content, msgType, extra);

        // 实时推送：对方在线则通过 WebSocket 即刻收到
        try {
            User fromUser = userService.getById(userId);
            Map<String, Object> pushData = new LinkedHashMap<>();
            pushData.put("id", msg.getId());
            pushData.put("from_user_id", msg.getFromUserId());
            pushData.put("to_user_id", msg.getToUserId());
            pushData.put("content", msg.getContent());
            pushData.put("msg_type", msg.getMsgType());
            pushData.put("extra", msg.getExtra());
            pushData.put("create_time", msg.getCreateTime() != null ? msg.getCreateTime().toString() : null);
            if (fromUser != null) {
                pushData.put("from_user", UserVO.from(fromUser));
            }
            sessionManager.pushBoth(userId, toUserId, objectMapper.writeValueAsString(pushData));
        } catch (Exception e) {
            log.error("WebSocket push failed after send: {}", e.getMessage());
        }

        return Result.ok(msg);
    }

    /** 获取与某用户的聊天记录 */
    @GetMapping("/history")
    public Result<List<MessageVO>> history(
            @RequestParam("with_user_id") Long withUserId,
            @RequestParam(defaultValue = "30") int pageSize,
            @RequestParam(required = false) Long beforeId,
            HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(messageService.getChatHistory(userId, withUserId, pageSize, beforeId));
    }

    /** 获取会话列表 */
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> conversations(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(messageService.getConversations(userId));
    }

    /** 标记某用户的聊天消息为已读 */
    @PostMapping("/read/{fromUserId}")
    public Result<?> markRead(@PathVariable Long fromUserId, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        messageService.markRead(userId, fromUserId);
        return Result.ok();
    }

    /** 获取未读私聊消息总数 */
    @GetMapping("/unread/count")
    public Result<Map<String, Long>> unreadCount(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(Map.of("count", messageService.getUnreadMessageCount(userId)));
    }

    /** ---------- 通知 ---------- */

    /** 获取通知列表 */
    @GetMapping("/notifications")
    public Result<List<NotificationVO>> notifications(
            @RequestParam(required = false) Integer type,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long beforeId,
            HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        List<NotificationVO> list = messageService.getNotifications(userId, type, pageSize, beforeId);
        log.info("[NOTIF-API] query: userId={} type={} resultSize={}", userId, type, list.size());
        return Result.ok(list);
    }

    /** 获取各类通知的未读数 */
    @GetMapping("/notifications/unread")
    public Result<Map<String, Long>> notificationUnread(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(messageService.getUnreadCounts(userId));
    }

    /** 标记通知为已读 */
    @PostMapping("/notifications/read")
    public Result<?> markNotificationRead(@RequestParam(required = false) Integer type, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        messageService.markNotificationRead(userId, type);
        return Result.ok();
    }
}
