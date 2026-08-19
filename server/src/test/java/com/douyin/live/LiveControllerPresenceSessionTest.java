package com.douyin.live;

import com.douyin.common.Result;
import com.douyin.config.LiveMediaProperties;
import com.douyin.controller.LiveController;
import com.douyin.engine.StreamingEngine;
import com.douyin.engine.StreamingSessionManager;
import com.douyin.entity.LiveRoom;
import com.douyin.mapper.FollowMapper;
import com.douyin.mapper.UserMapper;
import com.douyin.service.LiveMediaTokenService;
import com.douyin.service.LivePresenceService;
import com.douyin.service.LiveService;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class LiveControllerPresenceSessionTest {

    private LiveService liveService;
    private LivePresenceService presenceService;
    private LiveController controller;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        liveService = mock(LiveService.class);
        presenceService = mock(LivePresenceService.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        LiveMediaTokenService mediaTokenService = mock(LiveMediaTokenService.class);
        request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer access-token");
        when(jwtUtil.getUserIdFromToken("access-token")).thenReturn(7L);
        when(mediaTokenService.issue(any(), any(), any(), any()))
                .thenReturn(new LiveMediaTokenService.IssuedToken("", 0));
        controller = new LiveController(
                liveService,
                mock(UserMapper.class),
                mock(FollowMapper.class),
                jwtUtil,
                mock(StreamingSessionManager.class),
                mock(StreamingEngine.class),
                mock(LiveMediaProperties.class),
                mediaTokenService,
                presenceService);
    }

    @Test
    void joinAndLeaveRejectMissingPresenceSessionWithoutChangingPresence() {
        Result<?> join = controller.join(100L, null, request);
        Result<?> leave = controller.leave(100L, Map.of("sessionId", "   "), request);

        assertEquals(400, join.getCode());
        assertEquals(400, leave.getCode());
        verifyNoInteractions(liveService, presenceService);
    }

    @Test
    void acceptsExistingClientSessionIdAndUsesItAsThePresenceIdentity() {
        LiveRoom room = new LiveRoom();
        room.setId(100L);
        room.setStatus("LIVE");
        room.setSrtStreamId("room-100");
        when(liveService.getById(100L)).thenReturn(room);
        when(presenceService.touch(100L, 7L, "client-session-1")).thenReturn(true);
        when(presenceService.count(100L)).thenReturn(1);

        Result<?> result = controller.join(100L, Map.of("sessionId", " client-session-1 "), request);

        assertEquals(200, result.getCode());
        verify(presenceService).touch(100L, 7L, "client-session-1");
        verify(liveService).joinRoom(100L);
        verify(liveService, never()).leaveRoom(100L);
    }
}
