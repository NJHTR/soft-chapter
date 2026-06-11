package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.douyin.entity.LiveRoom;
import com.douyin.mapper.LiveRoomMapper;
import com.douyin.service.LiveService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class LiveServiceImpl extends ServiceImpl<LiveRoomMapper, LiveRoom> implements LiveService {

    /** 清理启动前遗留的 LIVE 状态房间（服务器重启导致 WebSocket 全部断开） */
    @PostConstruct
    public void cleanupOnStartup() {
        List<LiveRoom> stale = list(new LambdaQueryWrapper<LiveRoom>()
                .eq(LiveRoom::getStatus, "LIVE"));
        for (LiveRoom r : stale) {
            r.setStatus("ENDED");
            updateById(r);
            log.info("Startup cleanup: ended stale room id={}, host={}", r.getId(), r.getHostUserId());
        }
        if (!stale.isEmpty()) {
            log.info("Startup cleanup: ended {} stale LIVE rooms", stale.size());
        }
    }

    /** 定时清理异常未关播的房间（2 分钟未更新视为已断线，兜底机制） */
    @Scheduled(fixedRate = 60000)
    public void cleanupStaleRooms() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(2);
        List<LiveRoom> stale = list(new LambdaQueryWrapper<LiveRoom>()
                .eq(LiveRoom::getStatus, "LIVE")
                .lt(LiveRoom::getUpdateTime, threshold));
        for (LiveRoom r : stale) {
            r.setStatus("ENDED");
            updateById(r);
            log.info("Scheduled cleanup: ended stale room id={}, host={}", r.getId(), r.getHostUserId());
        }
    }

    @Override
    @Transactional
    public LiveRoom createRoom(Long hostUserId, String title, String coverUrl) {
        // 关闭该用户之前的直播间
        List<LiveRoom> oldRooms = list(new LambdaQueryWrapper<LiveRoom>()
                .eq(LiveRoom::getHostUserId, hostUserId)
                .eq(LiveRoom::getStatus, "LIVE"));
        for (LiveRoom r : oldRooms) {
            r.setStatus("ENDED");
            updateById(r);
        }

        LiveRoom room = new LiveRoom();
        room.setHostUserId(hostUserId);
        room.setTitle(title != null ? title : "");
        room.setCoverUrl(coverUrl != null ? coverUrl : "");
        room.setStatus("PREVIEW");
        room.setViewerCount(0);
        room.setTotalViewers(0);
        room.setLikeCount(0);
        save(room);
        log.info("Live room created: id={}, host={}", room.getId(), hostUserId);
        return room;
    }

    @Override
    @Transactional
    public LiveRoom startLive(Long roomId, Long hostUserId) {
        LiveRoom room = getById(roomId);
        if (room == null || !room.getHostUserId().equals(hostUserId)) return null;
        room.setStatus("LIVE");
        room.setUpdateTime(LocalDateTime.now());
        updateById(room);
        log.info("Live started: roomId={}", roomId);
        return room;
    }

    @Override
    @Transactional
    public LiveRoom endLive(Long roomId, Long hostUserId) {
        LiveRoom room = getById(roomId);
        if (room == null || !room.getHostUserId().equals(hostUserId)) return null;
        room.setStatus("ENDED");
        room.setUpdateTime(LocalDateTime.now());
        updateById(room);
        log.info("Live ended: roomId={}, totalViewers={}", roomId, room.getTotalViewers());
        return room;
    }

    @Override
    public LiveRoom joinRoom(Long roomId) {
        LiveRoom room = getById(roomId);
        if (room == null || !"LIVE".equals(room.getStatus())) return null;
        room.setViewerCount((room.getViewerCount() != null ? room.getViewerCount() : 0) + 1);
        int total = (room.getTotalViewers() != null ? room.getTotalViewers() : 0) + 1;
        room.setTotalViewers(total);
        updateById(room);
        return room;
    }

    @Override
    public void leaveRoom(Long roomId) {
        LiveRoom room = getById(roomId);
        if (room != null) {
            room.setViewerCount(Math.max(0, (room.getViewerCount() != null ? room.getViewerCount() : 0) - 1));
            updateById(room);
        }
    }

    @Override
    public void addLike(Long roomId) {
        LiveRoom room = getById(roomId);
        if (room != null) {
            room.setLikeCount((room.getLikeCount() != null ? room.getLikeCount() : 0) + 1);
            updateById(room);
        }
    }
}
