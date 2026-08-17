package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.douyin.config.LiveMediaProperties;
import com.douyin.engine.StreamingEngine;
import com.douyin.engine.StreamingSessionManager;
import com.douyin.entity.LiveRoom;
import com.douyin.mapper.LiveRoomMapper;
import com.douyin.service.LiveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class LiveServiceImpl extends ServiceImpl<LiveRoomMapper, LiveRoom> implements LiveService {

    private final StreamingSessionManager sessionManager;
    private final StreamingEngine streamingEngine;
    private final LiveMediaProperties mediaProperties;
    private final boolean nativeEngineEnabled;

    public LiveServiceImpl(StreamingSessionManager sessionManager,
                           StreamingEngine streamingEngine,
                           LiveMediaProperties mediaProperties,
                           @Value("${live.engine.enabled:false}") boolean nativeEngineEnabled) {
        this.sessionManager = sessionManager;
        this.streamingEngine = streamingEngine;
        this.mediaProperties = mediaProperties;
        this.nativeEngineEnabled = nativeEngineEnabled;
    }

    /*
     * Do not infer media liveness from update_time. A perfectly healthy WHIP
     * publisher can leave the room row unchanged for minutes, while a process
     * restart can make every row look stale at once. Provider callbacks and a
     * heartbeat/reconciliation job own this transition in the next live
     * control-plane task; until then rooms are ended explicitly by the host or
     * the authenticated control WS disconnect grace period.
     */

    @Override
    @Transactional
    public LiveRoom createRoom(Long hostUserId, String title, String coverUrl) {
        List<LiveRoom> oldRooms = list(new LambdaQueryWrapper<LiveRoom>()
                .eq(LiveRoom::getHostUserId, hostUserId)
                .eq(LiveRoom::getStatus, "LIVE"));
        for (LiveRoom r : oldRooms) {
            r.setStatus("ENDED");
            updateById(r);
            sessionManager.stopSession(r.getId());
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
        if ("LIVE".equals(room.getStatus())) return room;
        if (!"PREVIEW".equals(room.getStatus())) return null;

        // SRS owns the browser media path. The key is random per broadcast and
        // is never derived from a room id, so an old URL cannot publish again.
        String streamKey = "live_" + roomId + "_" + UUID.randomUUID().toString().replace("-", "");
        room.setSrtStreamId(streamKey);
        room.setRtmpStreamKey(streamKey);
        room.setStreamUrl(mediaProperties.rtmp(streamKey));
        room.setPlayUrl(mediaProperties.whep(streamKey));
        room.setTargetBitrate(2_500_000);
        room.setMaxBitrate(4_000_000);
        room.setMinBitrate(600_000);
        room.setEncoderType(nativeEngineEnabled ? "native-engine" : "browser-webrtc");
        room.setCodec("h264");
        room.setStatus("LIVE");
        room.setUpdateTime(LocalDateTime.now());

        // The native engine is an explicit opt-in for deployments that have a
        // real encoder and SRS ingest adapter. It must not claim success by
        // default while its fallback implementation emits no media.
        if (nativeEngineEnabled) {
            if (!streamingEngine.isNativeAvailable()) {
                throw new IllegalStateException("native streaming engine is enabled but unavailable");
            }
            StreamingEngine.EngineConfig config = StreamingEngine.EngineConfig.builder()
                    .inputWidth(1920).inputHeight(1080)
                    .outputWidth(1280).outputHeight(720)
                    .fps(30).bitrate(2_500_000).maxBitrate(4_000_000)
                    .minBitrate(600_000).enableDenoise(true).enableBeauty(false)
                    .enableSuperRes(false).enableHdr(false).encoderType("h264")
                    .codec("h264").streamProtocol("whip").streamKey(streamKey).build();
            var session = sessionManager.startSession(roomId, hostUserId, config);
            if (!session.start()) {
                sessionManager.stopSession(roomId);
                throw new IllegalStateException("native streaming engine failed to start");
            }
        }
        updateById(room);
        log.info("Live started with SRS WHIP: roomId={}", roomId);

        return room;
    }

    @Override
    @Transactional
    public LiveRoom endLive(Long roomId, Long hostUserId) {
        LiveRoom room = getById(roomId);
        if (room == null || !room.getHostUserId().equals(hostUserId)) return null;

        // Stop native streaming engine
        sessionManager.stopSession(roomId);

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
        update(new UpdateWrapper<LiveRoom>()
                .setSql("viewer_count = COALESCE(viewer_count, 0) + 1")
                .setSql("total_viewers = COALESCE(total_viewers, 0) + 1")
                .eq("id", roomId)
                .eq("status", "LIVE"));
        return getById(roomId);
    }

    @Override
    public void leaveRoom(Long roomId) {
        update(new UpdateWrapper<LiveRoom>()
                .setSql("viewer_count = CASE WHEN COALESCE(viewer_count, 0) > 0 THEN viewer_count - 1 ELSE 0 END")
                .eq("id", roomId)
                .eq("status", "LIVE"));
    }

    @Override
    public void addLike(Long roomId) {
        update(new UpdateWrapper<LiveRoom>()
                .setSql("like_count = COALESCE(like_count, 0) + 1")
                .eq("id", roomId)
                .eq("status", "LIVE"));
    }
}
