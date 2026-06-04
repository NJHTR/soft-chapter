package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.douyin.entity.Message;
import com.douyin.entity.Notification;
import com.douyin.entity.User;
import com.douyin.mapper.MessageMapper;
import com.douyin.mapper.NotificationMapper;
import com.douyin.service.MessageService;
import com.douyin.service.UserService;
import com.douyin.vo.ConversationVO;
import com.douyin.vo.MessageVO;
import com.douyin.vo.NotificationVO;
import com.douyin.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    private final NotificationMapper notificationMapper;
    private final UserService userService;

    public MessageServiceImpl(NotificationMapper notificationMapper, UserService userService) {
        this.notificationMapper = notificationMapper;
        this.userService = userService;
    }

    @Override
    public Message sendMessage(Long fromUserId, Long toUserId, String content, Integer msgType, String extra) {
        Message msg = new Message();
        msg.setFromUserId(fromUserId);
        msg.setToUserId(toUserId);
        msg.setContent(content != null ? content : "");
        msg.setMsgType(msgType != null ? msgType : 1);
        msg.setExtra(extra != null ? extra : "");
        msg.setIsRead(0);
        save(msg);
        return msg;
    }

    @Override
    public List<MessageVO> getChatHistory(Long userId1, Long userId2, int pageSize, Long beforeId) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
                .and(w1 -> w1.eq(Message::getFromUserId, userId1).eq(Message::getToUserId, userId2))
                .or(w1 -> w1.eq(Message::getFromUserId, userId2).eq(Message::getToUserId, userId1)));
        if (beforeId != null && beforeId > 0) {
            wrapper.lt(Message::getId, beforeId);
        }
        wrapper.orderByDesc(Message::getId);
        wrapper.last("LIMIT " + pageSize);

        List<Message> messages = list(wrapper);
        Collections.reverse(messages); // 正序返回

        // 批量查用户信息
        Set<Long> userIds = new HashSet<>();
        messages.forEach(m -> {
            userIds.add(m.getFromUserId());
            userIds.add(m.getToUserId());
        });
        Map<Long, UserVO> userMap = getUserVOMap(userIds);

        return messages.stream()
                .map(m -> MessageVO.from(m, userMap.get(m.getFromUserId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<ConversationVO> getConversations(Long userId) {
        // 找到所有与我有消息往来的用户(读取最近100条消息提取)
        LambdaQueryWrapper<Message> recent = new LambdaQueryWrapper<>();
        recent.and(w -> w
                .eq(Message::getFromUserId, userId)
                .or()
                .eq(Message::getToUserId, userId));
        recent.select(Message::getFromUserId, Message::getToUserId);
        recent.orderByDesc(Message::getId);
        recent.last("LIMIT 200");
        List<Message> recentMessages = list(recent);

        Set<Long> targetIds = new HashSet<>();
        for (Message m : recentMessages) {
            if (m.getFromUserId().equals(userId)) {
                targetIds.add(m.getToUserId());
            } else {
                targetIds.add(m.getFromUserId());
            }
        }

        if (targetIds.isEmpty()) return List.of();

        List<ConversationVO> result = new ArrayList<>();
        for (Long targetId : targetIds) {
            // 查该对话最后一条消息
            LambdaQueryWrapper<Message> lastMsg = new LambdaQueryWrapper<>();
            lastMsg.and(w -> w
                    .and(w1 -> w1.eq(Message::getFromUserId, userId).eq(Message::getToUserId, targetId))
                    .or(w1 -> w1.eq(Message::getFromUserId, targetId).eq(Message::getToUserId, userId)));
            lastMsg.orderByDesc(Message::getId);
            lastMsg.last("LIMIT 1");
            Message last = getOne(lastMsg);

            // 未读数
            long unread = count(new LambdaQueryWrapper<Message>()
                    .eq(Message::getFromUserId, targetId)
                    .eq(Message::getToUserId, userId)
                    .eq(Message::getIsRead, 0));

            User user = userService.getById(targetId);
            UserVO targetVO = user != null ? UserVO.from(user) : null;

            ConversationVO vo = new ConversationVO();
            vo.setTargetUser(targetVO);
            vo.setLastMessage(last != null ? last.getContent() : "");
            vo.setLastMsgType(last != null ? last.getMsgType() : 1);
            vo.setLastTime(last != null ? last.getCreateTime() : null);
            vo.setUnreadCount(unread);
            result.add(vo);
        }

        result.sort((a, b) -> {
            if (a.getLastTime() == null) return 1;
            if (b.getLastTime() == null) return -1;
            return b.getLastTime().compareTo(a.getLastTime());
        });
        return result;
    }

    @Override
    public void markRead(Long userId, Long fromUserId) {
        Message update = new Message();
        update.setIsRead(1);
        update(update, new LambdaQueryWrapper<Message>()
                .eq(Message::getFromUserId, fromUserId)
                .eq(Message::getToUserId, userId)
                .eq(Message::getIsRead, 0));
    }

    @Override
    public long getUnreadMessageCount(Long userId) {
        return count(new LambdaQueryWrapper<Message>()
                .eq(Message::getToUserId, userId)
                .eq(Message::getIsRead, 0));
    }

    @Override
    public Notification createNotification(Notification notification) {
        // 自己不给自己发通知
        if (notification.getUserId().equals(notification.getFromUserId())) {
            log.info("[NOTIF-DB] skip self-notify: userId={} fromUserId={}", notification.getUserId(), notification.getFromUserId());
            return null;
        }
        notification.setIsRead(0);
        int rows = notificationMapper.insert(notification);
        log.info("[NOTIF-DB] inserted: id={} userId={} fromUserId={} type={} rows={}",
                notification.getId(), notification.getUserId(), notification.getFromUserId(),
                notification.getType(), rows);
        return notification;
    }

    @Override
    public List<NotificationVO> getNotifications(Long userId, Integer type, int pageSize, Long beforeId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        if (type != null && type > 0) {
            wrapper.eq(Notification::getType, type);
        }
        if (beforeId != null && beforeId > 0) {
            wrapper.lt(Notification::getId, beforeId);
        }
        wrapper.orderByDesc(Notification::getId);
        wrapper.last("LIMIT " + pageSize);

        List<Notification> notifications = notificationMapper.selectList(wrapper);

        Set<Long> userIds = new HashSet<>();
        notifications.forEach(n -> userIds.add(n.getFromUserId()));
        Map<Long, UserVO> userMap = getUserVOMap(userIds);

        return notifications.stream()
                .map(n -> NotificationVO.from(n, userMap.get(n.getFromUserId())))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Long> getUnreadCounts(Long userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        // 全部未读
        counts.put("all", notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)));
        // 赞
        counts.put("like", notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
                        .eq(Notification::getType, NotificationVO.TYPE_LIKE)));
        // 评论
        counts.put("comment", notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
                        .eq(Notification::getType, NotificationVO.TYPE_COMMENT)));
        // 关注
        counts.put("follow", notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
                        .eq(Notification::getType, NotificationVO.TYPE_FOLLOW)));
        // @我的
        counts.put("at", notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
                        .eq(Notification::getType, NotificationVO.TYPE_AT)));
        return counts;
    }

    @Override
    public void markNotificationRead(Long userId, Integer type) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId).eq(Notification::getIsRead, 0);
        if (type != null && type > 0) {
            wrapper.eq(Notification::getType, type);
        }
        Notification update = new Notification();
        update.setIsRead(1);
        notificationMapper.update(update, wrapper);
    }

    @Override
    public long getUnreadNotificationCount(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
    }

    private Map<Long, UserVO> getUserVOMap(Set<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        List<User> users = userService.listByIds(userIds);
        return users.stream().collect(Collectors.toMap(User::getUid, UserVO::from, (a, b) -> a));
    }
}
