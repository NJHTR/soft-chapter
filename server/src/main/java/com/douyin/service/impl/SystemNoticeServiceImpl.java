package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.douyin.entity.SystemNotice;
import com.douyin.mapper.SystemNoticeMapper;
import com.douyin.service.SystemNoticeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SystemNoticeServiceImpl extends ServiceImpl<SystemNoticeMapper, SystemNotice> implements SystemNoticeService {

    @Override
    public List<SystemNotice> findByUserId(Long userId, int pageNo, int pageSize) {
        int offset = (pageNo - 1) * pageSize;
        return baseMapper.findByUserId(userId, offset, pageSize);
    }

    @Override
    public int countUnread(Long userId) {
        return baseMapper.countUnread(userId);
    }

    @Override
    @Transactional
    public void markAsRead(Long id, Long userId) {
        update(new LambdaUpdateWrapper<SystemNotice>()
                .eq(SystemNotice::getId, id)
                .eq(SystemNotice::getUserId, userId)
                .set(SystemNotice::getIsRead, 1));
    }

    @Override
    @Transactional
    public void send(Long userId, String type, String title, String content) {
        SystemNotice notice = new SystemNotice();
        notice.setUserId(userId);
        notice.setType(type);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setIsRead(0);
        save(notice);
    }
}
