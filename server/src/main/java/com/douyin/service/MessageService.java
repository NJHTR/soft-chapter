package com.douyin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.douyin.entity.Message;
import com.douyin.entity.Notification;
import com.douyin.vo.MessageVO;
import com.douyin.vo.NotificationVO;
import com.douyin.vo.ConversationVO;

import java.util.List;

public interface MessageService extends IService<Message> {

    /** 发送私聊消息 */
    Message sendMessage(Long fromUserId, Long toUserId, String content, Integer msgType, String extra);

    /** 获取与某用户的聊天记录 */
    List<MessageVO> getChatHistory(Long userId1, Long userId2, int pageSize, Long beforeId);

    /** 获取当前用户的会话列表(每个会话最新一条消息) */
    List<ConversationVO> getConversations(Long userId);

    /** 标记消息为已读 */
    void markRead(Long userId, Long fromUserId);

    /** 获取未读私聊消息总数 */
    long getUnreadMessageCount(Long userId);

    /** ---------- 通知 ---------- */

    /** 创建通知 */
    Notification createNotification(Notification notification);

    /** 获取通知列表(按类型筛选) */
    List<NotificationVO> getNotifications(Long userId, Integer type, int pageSize, Long beforeId);

    /** 获取各类通知的未读数 */
    java.util.Map<String, Long> getUnreadCounts(Long userId);

    /** 标记通知为已读 */
    void markNotificationRead(Long userId, Integer type);

    /** 获取未读通知总数 */
    long getUnreadNotificationCount(Long userId);
}
