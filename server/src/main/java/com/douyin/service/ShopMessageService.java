package com.douyin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.douyin.entity.ShopMessage;
import com.douyin.mapper.ShopMessageMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShopMessageService {

    private final ShopMessageMapper shopMessageMapper;

    public ShopMessageService(ShopMessageMapper shopMessageMapper) {
        this.shopMessageMapper = shopMessageMapper;
    }

    /** 创建消息 */
    public void create(Long userId, String title, String content, String type, Long relatedId) {
        ShopMessage msg = new ShopMessage();
        msg.setUserId(userId);
        msg.setTitle(title);
        msg.setContent(content);
        msg.setType(type);
        msg.setRelatedId(relatedId);
        msg.setIsRead(0);
        shopMessageMapper.insert(msg);
    }

    /** 用户消息列表 (最新在前) */
    public List<ShopMessage> listByUser(Long userId) {
        return shopMessageMapper.selectList(new LambdaQueryWrapper<ShopMessage>()
                .eq(ShopMessage::getUserId, userId)
                .orderByDesc(ShopMessage::getCreateTime));
    }

    /** 未读数量 */
    public int unreadCount(Long userId) {
        Long count = shopMessageMapper.selectCount(new LambdaQueryWrapper<ShopMessage>()
                .eq(ShopMessage::getUserId, userId)
                .eq(ShopMessage::getIsRead, 0));
        return count != null ? count.intValue() : 0;
    }

    /** 标记已读 */
    public void markRead(Long id, Long userId) {
        ShopMessage msg = shopMessageMapper.selectById(id);
        if (msg != null && msg.getUserId().equals(userId)) {
            msg.setIsRead(1);
            shopMessageMapper.updateById(msg);
        }
    }

    /** 全部已读 */
    public void markAllRead(Long userId) {
        List<ShopMessage> unread = shopMessageMapper.selectList(new LambdaQueryWrapper<ShopMessage>()
                .eq(ShopMessage::getUserId, userId)
                .eq(ShopMessage::getIsRead, 0));
        for (ShopMessage m : unread) {
            m.setIsRead(1);
            shopMessageMapper.updateById(m);
        }
    }
}
