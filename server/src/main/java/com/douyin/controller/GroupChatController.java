package com.douyin.controller;

import com.douyin.common.Result;
import com.douyin.entity.Group;
import com.douyin.entity.GroupMember;
import com.douyin.entity.GroupMessage;
import com.douyin.entity.User;
import com.douyin.service.GroupChatService;
import com.douyin.service.UserService;
import com.douyin.utils.JwtUtil;
import com.douyin.vo.*;
import com.douyin.websocket.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/group")
public class GroupChatController {

    private final GroupChatService groupChatService;
    private final JwtUtil jwtUtil;
    private final SessionManager sessionManager;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public GroupChatController(GroupChatService groupChatService, JwtUtil jwtUtil,
                               SessionManager sessionManager, UserService userService,
                               ObjectMapper objectMapper) {
        this.groupChatService = groupChatService;
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

    /** 创建群聊 */
    @PostMapping("/create")
    public Result<GroupVO> create(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");

        String name = (String) body.getOrDefault("name", "");
        if (name.isBlank()) return Result.fail("群名称不能为空");

        @SuppressWarnings("unchecked")
        List<?> memberIdsRaw = (List<?>) body.get("member_uids");
        List<Long> memberUids = memberIdsRaw != null
                ? memberIdsRaw.stream().map(o -> Long.valueOf(o.toString())).collect(Collectors.toList())
                : List.of();

        Group group = groupChatService.createGroup(userId, name, memberUids);
        return Result.ok(GroupVO.from(group));
    }

    /** 获取我加入的群聊列表 */
    @GetMapping("/list")
    public Result<List<GroupVO>> list(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(groupChatService.getGroupList(userId));
    }

    /** 获取群聊会话列表(带最后消息和未读数) */
    @GetMapping("/conversations")
    public Result<List<GroupConversationVO>> conversations(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(groupChatService.getGroupConversations(userId));
    }

    /** 获取群信息 */
    @GetMapping("/{groupId}/info")
    public Result<GroupVO> info(@PathVariable Long groupId, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        GroupVO vo = groupChatService.getGroupInfo(groupId);
        return vo != null ? Result.ok(vo) : Result.fail("群聊不存在");
    }

    /** 获取群成员 */
    @GetMapping("/{groupId}/members")
    public Result<List<GroupMemberVO>> members(@PathVariable Long groupId, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(groupChatService.getGroupMembers(groupId));
    }

    /** 发送群消息 */
    @PostMapping("/{groupId}/message/send")
    public Result<GroupMessage> sendMessage(@PathVariable Long groupId,
                                            @RequestBody Map<String, Object> body,
                                            HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");

        String content = (String) body.getOrDefault("content", "");
        Integer msgType = body.get("msg_type") != null ? Integer.valueOf(body.get("msg_type").toString()) : 1;
        String extra = (String) body.getOrDefault("extra", "");

        try {
            GroupMessage msg = groupChatService.sendGroupMessage(groupId, userId, content, msgType, extra);

            // 推送给其他在线群成员（排除发送者自己，避免重复）
            try {
                List<GroupMemberVO> members = groupChatService.getGroupMembers(groupId);
                List<Long> memberUids = members.stream().map(GroupMemberVO::getUserId).collect(Collectors.toList());
                User fromUser = userService.getById(userId);

                Map<String, Object> pushData = new LinkedHashMap<>();
                pushData.put("id", msg.getId());
                pushData.put("group_id", msg.getGroupId());
                pushData.put("from_user_id", msg.getFromUserId());
                pushData.put("content", msg.getContent());
                pushData.put("msg_type", msg.getMsgType());
                pushData.put("extra", msg.getExtra());
                pushData.put("create_time", msg.getCreateTime() != null ? msg.getCreateTime().toString() : null);
                if (fromUser != null) {
                    pushData.put("from_user", UserVO.from(fromUser));
                }
                pushData.put("type", "group_message");
                String json = objectMapper.writeValueAsString(pushData);
                // 推送给除发送者外的其他群成员
                sessionManager.pushToGroupMembers(memberUids, userId, json);
            } catch (Exception e) {
                log.error("Group message push failed: {}", e.getMessage());
            }

            return Result.ok(msg);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 获取群消息历史 */
    @GetMapping("/{groupId}/message/history")
    public Result<List<GroupMessageVO>> history(@PathVariable Long groupId,
                                                 @RequestParam(defaultValue = "30") int pageSize,
                                                 @RequestParam(required = false) Long beforeId,
                                                 HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(groupChatService.getGroupMessageHistory(groupId, userId, pageSize, beforeId));
    }

    /** 标记群消息已读 */
    @PostMapping("/{groupId}/read")
    public Result<?> markRead(@PathVariable Long groupId,
                              @RequestBody Map<String, Object> body,
                              HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        Long lastMsgId = body.get("last_msg_id") != null ? Long.valueOf(body.get("last_msg_id").toString()) : 0L;
        groupChatService.markGroupRead(groupId, userId, lastMsgId);
        return Result.ok();
    }

    /** 邀请成员入群 */
    @PostMapping("/{groupId}/add-members")
    public Result<?> addMembers(@PathVariable Long groupId,
                                 @RequestBody Map<String, Object> body,
                                 HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");

        @SuppressWarnings("unchecked")
        List<?> memberIdsRaw = (List<?>) body.get("member_uids");
        List<Long> memberUids = memberIdsRaw != null
                ? memberIdsRaw.stream().map(o -> Long.valueOf(o.toString())).collect(Collectors.toList())
                : List.of();

        try {
            groupChatService.addMembers(groupId, userId, memberUids);
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 退出群聊 */
    @PostMapping("/{groupId}/leave")
    public Result<?> leave(@PathVariable Long groupId, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        groupChatService.leaveGroup(groupId, userId);
        return Result.ok();
    }

    /** 移除成员 */
    @PostMapping("/{groupId}/remove-member")
    public Result<?> removeMember(@PathVariable Long groupId,
                                   @RequestBody Map<String, Object> body,
                                   HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        Long targetUid = body.get("target_uid") != null ? Long.valueOf(body.get("target_uid").toString()) : null;
        if (targetUid == null) return Result.fail("请指定要移除的成员");

        try {
            groupChatService.removeMember(groupId, userId, targetUid);
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 搜索群聊 */
    @GetMapping("/search")
    public Result<List<GroupVO>> search(@RequestParam String keyword, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(groupChatService.searchGroups(userId, keyword));
    }
}
