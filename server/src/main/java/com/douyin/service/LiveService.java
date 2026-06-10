package com.douyin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.douyin.entity.LiveRoom;

public interface LiveService extends IService<LiveRoom> {
    LiveRoom createRoom(Long hostUserId, String title, String coverUrl);
    LiveRoom startLive(Long roomId, Long hostUserId);
    LiveRoom endLive(Long roomId, Long hostUserId);
    LiveRoom joinRoom(Long roomId);
    void leaveRoom(Long roomId);
    void addLike(Long roomId);
}
