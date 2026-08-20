package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
    private final long providerGraceSeconds;

    public LiveServiceImpl(StreamingSessionManager sessionManager,
                           StreamingEngine streamingEngine,
                           LiveMediaProperties mediaProperties,
                           @Value("${live.engine.enabled:false}") boolean nativeEngineEnabled,
                           @Value("${live.media.reconciliation.grace-seconds:45}") long providerGraceSeconds) {
        this.sessionManager = sessionManager;
        this.streamingEngine = streamingEngine;
        this.mediaProperties = mediaProperties;
        this.nativeEngineEnabled = nativeEngineEnabled;
        this.providerGraceSeconds = Math.max(15, Math.min(providerGraceSeconds, 300));
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
                .in(LiveRoom::getStatus, List.of("STARTING", "LIVE", "DEGRADED")));
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
        // Ingest URLs are minted per authenticated request; never persist a
        // reusable stream-key URL in the room row.
        room.setStreamUrl("");
        // Playback URLs are minted per authenticated viewer; do not persist a
        // reusable stream-key URL in the room row.
        room.setPlayUrl("");
        room.setTargetBitrate(2_500_000);
        room.setMaxBitrate(4_000_000);
        room.setMinBitrate(600_000);
        room.setEncoderType(nativeEngineEnabled ? "native-engine" : "browser-webrtc");
        room.setCodec("h264");
        room.setStatus("STARTING");
        room.setProviderState("STARTING");
        room.setProviderSessionId(null);
        room.setProviderLastSeenAt(null);
        // A room that never reaches an authenticated on_publish callback also
        // needs bounded convergence. This timestamp is a control-plane start
        // deadline, not a proxy for media activity or update_time.
        room.setProviderGraceUntil(LocalDateTime.now().plusSeconds(providerGraceSeconds));
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
            room.setStatus("LIVE");
            room.setProviderState("ACTIVE");
            room.setProviderLastSeenAt(LocalDateTime.now());
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

        // PREVIEW has no provider generation to drain. Keep this terminal path
        // for failed starts, while a started room first enters ENDING so the
        // exact provider generation can close it or reconciliation can expire
        // it after the bounded grace window.
        if ("PREVIEW".equals(room.getStatus())) {
            LocalDateTime now = LocalDateTime.now();
            LambdaUpdateWrapper<LiveRoom> terminal = new LambdaUpdateWrapper<LiveRoom>()
                    .set(LiveRoom::getStatus, "ENDED")
                    .set(LiveRoom::getProviderState, "ENDED")
                    .set(LiveRoom::getProviderGraceUntil, null)
                    .set(LiveRoom::getProviderLastSeenAt, now)
                    .set(LiveRoom::getUpdateTime, now)
                    .eq(LiveRoom::getId, roomId)
                    .eq(LiveRoom::getHostUserId, hostUserId)
                    .eq(LiveRoom::getStatus, "PREVIEW");
            if (baseMapper.update(null, terminal) == 1) {
                return getById(roomId);
            }
            return getById(roomId);
        }

        if ("ENDING".equals(room.getStatus()) || "ENDED".equals(room.getStatus())) {
            return room;
        }
        if (!isProviderMutableStatus(room.getStatus())) return null;

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<LiveRoom> ending = new LambdaUpdateWrapper<LiveRoom>()
                .set(LiveRoom::getStatus, "ENDING")
                .set(LiveRoom::getProviderState, "ENDING")
                .set(LiveRoom::getProviderGraceUntil, now.plusSeconds(providerGraceSeconds))
                .set(LiveRoom::getProviderLastSeenAt, now)
                .set(LiveRoom::getUpdateTime, now)
                .eq(LiveRoom::getId, roomId)
                .eq(LiveRoom::getHostUserId, hostUserId)
                .in(LiveRoom::getStatus, List.of("STARTING", "LIVE", "DEGRADED"))
                .eq(LiveRoom::getSrtStreamId, room.getSrtStreamId());
        appendProviderGenerationCas(ending, room.getProviderSessionId());

        // The provider generation predicate makes a delayed end request a
        // no-op if a replacement publisher won the room between the read and
        // this update. Do not stop a session that belongs to that replacement.
        if (baseMapper.update(null, ending) != 1) {
            return getById(roomId);
        }
        sessionManager.stopSession(roomId);
        LiveRoom ended = getById(roomId);
        log.info("Live ending: roomId={}, totalViewers={}", roomId,
                ended == null ? room.getTotalViewers() : ended.getTotalViewers());
        return ended;
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

    @Override
    @Transactional
    public LiveRoom providerStarted(Long roomId, String streamKey, String providerSessionId) {
        LiveRoom room = getById(roomId);
        if (!matchesProvider(room, streamKey) || "ENDED".equals(room.getStatus())
                || "ENDING".equals(room.getStatus())
                || isBlank(providerSessionId)) return null;
        if ("LIVE".equals(room.getStatus())
                && !isBlank(room.getProviderSessionId())
                && !sameProviderSession(room.getProviderSessionId(), providerSessionId)) {
            // A different client on the same SRS server is a concurrent
            // publisher and must not steal the room. A changed server prefix
            // identifies an SRS restart; the session projection has already
            // retired the old generation before this CAS transition.
            if (sameProviderServer(room.getProviderSessionId(), providerSessionId)) return null;
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<LiveRoom> transition = new LambdaUpdateWrapper<LiveRoom>()
                .set(LiveRoom::getStatus, "LIVE")
                .set(LiveRoom::getProviderState, "ACTIVE")
                .set(LiveRoom::getProviderSessionId, normalizeProviderId(providerSessionId))
                .set(LiveRoom::getProviderLastSeenAt, now)
                .set(LiveRoom::getProviderGraceUntil, null)
                .eq(LiveRoom::getId, roomId)
                .in(LiveRoom::getStatus, List.of("STARTING", "LIVE", "DEGRADED"))
                .eq(LiveRoom::getSrtStreamId, streamKey);
        if (isBlank(room.getProviderSessionId())) {
            // Rows created before RTC-006 used an empty string as the
            // provider-session sentinel. Accept both legacy representations
            // during the forward-migration window, then persist the real
            // generation below so later heartbeats remain strict.
            transition.and(w -> w.isNull(LiveRoom::getProviderSessionId)
                    .or().eq(LiveRoom::getProviderSessionId, ""));
        } else {
            transition.eq(LiveRoom::getProviderSessionId, normalizeProviderId(room.getProviderSessionId()));
        }
        int affected = baseMapper.update(null, transition);
        if (affected != 1) return null;
        return getById(roomId);
    }

    @Override
    @Transactional
    public LiveRoom providerHeartbeat(Long roomId, String streamKey, String providerSessionId) {
        LiveRoom room = getById(roomId);
        if (!matchesProvider(room, streamKey) || "ENDED".equals(room.getStatus())
                || "ENDING".equals(room.getStatus())
                || !sameProviderSession(room.getProviderSessionId(), providerSessionId)) return null;
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<LiveRoom> update = new LambdaUpdateWrapper<LiveRoom>()
                .set(LiveRoom::getStatus, "LIVE")
                .set(LiveRoom::getProviderState, "ACTIVE")
                .set(LiveRoom::getProviderLastSeenAt, now)
                .set(LiveRoom::getProviderGraceUntil, null)
                .eq(LiveRoom::getId, roomId)
                .eq(LiveRoom::getSrtStreamId, streamKey)
                .eq(LiveRoom::getProviderSessionId, normalizeProviderId(providerSessionId))
                .in(LiveRoom::getStatus, List.of("STARTING", "LIVE", "DEGRADED"));
        if (baseMapper.update(null, update) != 1) return null;
        return getById(roomId);
    }

    @Override
    @Transactional
    public LiveRoom providerDisconnected(Long roomId, String streamKey, String providerSessionId) {
        LiveRoom room = getById(roomId);
        boolean initialStart = isInitialProviderStart(room, providerSessionId);
        if (!matchesProvider(room, streamKey) || "ENDED".equals(room.getStatus())
                || (!initialStart && !sameProviderSession(room.getProviderSessionId(), providerSessionId))) return null;
        LocalDateTime now = LocalDateTime.now();
        if ("ENDING".equals(room.getStatus())) {
            LambdaUpdateWrapper<LiveRoom> terminal = new LambdaUpdateWrapper<LiveRoom>()
                    .set(LiveRoom::getStatus, "ENDED")
                    .set(LiveRoom::getProviderState, "ENDED")
                    .set(LiveRoom::getProviderGraceUntil, null)
                    .set(LiveRoom::getProviderLastSeenAt, now)
                    .set(LiveRoom::getUpdateTime, now)
                    .eq(LiveRoom::getId, roomId)
                    .eq(LiveRoom::getSrtStreamId, streamKey)
                    .eq(LiveRoom::getStatus, "ENDING");
            if (!initialStart) {
                terminal.eq(LiveRoom::getProviderSessionId, normalizeProviderId(providerSessionId));
            } else {
                terminal.and(w -> w.isNull(LiveRoom::getProviderSessionId)
                        .or().eq(LiveRoom::getProviderSessionId, ""));
            }
            if (baseMapper.update(null, terminal) != 1) return null;
            sessionManager.stopSession(roomId);
            return getById(roomId);
        }
        LambdaUpdateWrapper<LiveRoom> update = new LambdaUpdateWrapper<LiveRoom>()
                .set(LiveRoom::getProviderState, "DISCONNECTED")
                .set(LiveRoom::getProviderLastSeenAt, now)
                .set(LiveRoom::getProviderGraceUntil, now.plusSeconds(providerGraceSeconds))
                .set(LiveRoom::getStatus, "DEGRADED")
                .eq(LiveRoom::getId, roomId)
                .eq(LiveRoom::getSrtStreamId, streamKey)
                .eq(!initialStart, LiveRoom::getProviderSessionId, normalizeProviderId(providerSessionId))
                .in(LiveRoom::getStatus, List.of("STARTING", "LIVE", "DEGRADED"));
        if (baseMapper.update(null, update) != 1) return null;
        return getById(roomId);
    }

    @Override
    @Transactional
    public LiveRoom providerUnavailable(Long roomId, String streamKey) {
        LiveRoom room = getById(roomId);
        if (!matchesProvider(room, streamKey) || "ENDED".equals(room.getStatus())) return null;
        if ("ENDING".equals(room.getStatus())) return room;
        LambdaUpdateWrapper<LiveRoom> transition = new LambdaUpdateWrapper<LiveRoom>()
                .set(LiveRoom::getProviderState, "UNAVAILABLE")
                .set(LiveRoom::getStatus, "DEGRADED")
                // A provider API outage must not consume an earlier disconnect
                // deadline. Start a new bounded grace interval only after the
                // provider becomes reachable and confirms the stream is absent.
                .set(LiveRoom::getProviderGraceUntil, null)
                .eq(LiveRoom::getId, roomId)
                .eq(LiveRoom::getSrtStreamId, streamKey)
                .in(LiveRoom::getStatus, List.of("STARTING", "LIVE", "DEGRADED"));
        if (isBlank(room.getProviderSessionId())) {
            // Reconciliation may encounter a pre-RTC-006 row that still has
            // the old empty-string default. Treat it the same as SQL NULL;
            // this path carries no provider generation identity.
            transition.and(w -> w.isNull(LiveRoom::getProviderSessionId)
                    .or().eq(LiveRoom::getProviderSessionId, ""));
        } else {
            transition.eq(LiveRoom::getProviderSessionId, normalizeProviderId(room.getProviderSessionId()));
        }
        int affected = baseMapper.update(null, transition);
        if (affected != 1) return null;
        return getById(roomId);
    }

    @Override
    @Transactional
    public LiveRoom endProviderRoom(Long roomId, String streamKey, String providerSessionId, String reason) {
        LiveRoom room = getById(roomId);
        boolean initialStart = isInitialProviderStart(room, providerSessionId);
        if (!matchesProvider(room, streamKey)
                || (!initialStart && !sameProviderSession(room.getProviderSessionId(), providerSessionId))) return null;
        int affected = baseMapper.update(null, new LambdaUpdateWrapper<LiveRoom>()
                .set(LiveRoom::getStatus, "ENDED")
                .set(LiveRoom::getProviderState, "ENDED")
                .set(LiveRoom::getProviderGraceUntil, null)
                .set(LiveRoom::getProviderLastSeenAt, LocalDateTime.now())
                .eq(LiveRoom::getId, roomId)
                .eq(LiveRoom::getSrtStreamId, streamKey)
                .eq(!initialStart, LiveRoom::getProviderSessionId, normalizeProviderId(providerSessionId))
                .in(LiveRoom::getStatus, List.of("STARTING", "LIVE", "DEGRADED", "ENDING")));
        if (affected != 1) return null;
        log.info("Live provider room ended: roomId={}, reason={}", roomId, reason);
        sessionManager.stopSession(roomId);
        return getById(roomId);
    }

    @Override
    public List<LiveRoom> listProviderRooms() {
        return list(new LambdaQueryWrapper<LiveRoom>()
                .in(LiveRoom::getStatus, List.of("STARTING", "LIVE", "DEGRADED", "ENDING"))
                .isNotNull(LiveRoom::getSrtStreamId));
    }

    private static void appendProviderGenerationCas(LambdaUpdateWrapper<LiveRoom> update,
                                                    String providerSessionId) {
        if (isBlank(providerSessionId)) {
            update.and(w -> w.isNull(LiveRoom::getProviderSessionId)
                    .or().eq(LiveRoom::getProviderSessionId, ""));
        } else {
            update.eq(LiveRoom::getProviderSessionId, normalizeProviderId(providerSessionId));
        }
    }

    private static boolean matchesProvider(LiveRoom room, String streamKey) {
        return room != null && streamKey != null && streamKey.equals(room.getSrtStreamId());
    }

    private static String normalizeProviderId(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.trim().length() <= 128 ? value.trim() : value.trim().substring(0, 128);
    }

    private static boolean sameProviderSession(String current, String candidate) {
        return !isBlank(current) && !isBlank(candidate)
                && normalizeProviderId(current).equals(normalizeProviderId(candidate));
    }

    private static boolean sameProviderServer(String current, String candidate) {
        int currentSeparator = current == null ? -1 : current.indexOf(':');
        int candidateSeparator = candidate == null ? -1 : candidate.indexOf(':');
        return currentSeparator > 0 && candidateSeparator > 0
                && current.substring(0, currentSeparator).equals(candidate.substring(0, candidateSeparator));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isInitialProviderStart(LiveRoom room, String providerSessionId) {
        return room != null && ("STARTING".equals(room.getStatus()) || "DEGRADED".equals(room.getStatus())
                || "ENDING".equals(room.getStatus()))
                && isBlank(room.getProviderSessionId()) && isBlank(providerSessionId);
    }

    private static boolean isProviderMutableStatus(String status) {
        return "STARTING".equals(status) || "LIVE".equals(status) || "DEGRADED".equals(status);
    }
}
