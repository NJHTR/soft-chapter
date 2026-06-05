package com.douyin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.douyin.entity.SystemNotice;

import java.util.List;

public interface SystemNoticeService extends IService<SystemNotice> {

    List<SystemNotice> findByUserId(Long userId, int pageNo, int pageSize);

    int countUnread(Long userId);

    void markAsRead(Long id, Long userId);

    /** 发送系统通知 */
    void send(Long userId, String type, String title, String content);
}
