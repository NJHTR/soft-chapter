package com.douyin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.douyin.entity.LiveRoom;

import java.util.List;

public interface LiveService extends IService<LiveRoom> {
    LiveRoom createRoom(Long hostUserId, String title, String coverUrl);
    LiveRoom startLive(Long roomId, Long hostUserId);
    LiveRoom endLive(Long roomId, Long hostUserId);
    LiveRoom joinRoom(Long roomId);
    void leaveRoom(Long roomId);
    void addLike(Long roomId);

    /** Provider callback/reconciliation transitions. */
    LiveRoom providerStarted(Long roomId, String streamKey, String providerSessionId);
    LiveRoom providerHeartbeat(Long roomId, String streamKey, String providerSessionId);
    LiveRoom providerDisconnected(Long roomId, String streamKey, String providerSessionId);
    LiveRoom providerUnavailable(Long roomId, String streamKey);
    LiveRoom endProviderRoom(Long roomId, String streamKey, String providerSessionId, String reason);
    List<LiveRoom> listProviderRooms();
}
