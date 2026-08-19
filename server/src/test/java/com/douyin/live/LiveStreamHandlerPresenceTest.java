package com.douyin.live;

import com.douyin.entity.LiveRoom;
import com.douyin.service.LivePresenceService;
import com.douyin.service.LiveService;
import com.douyin.websocket.LiveStreamHandler;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiveStreamHandlerPresenceTest {

    @Test
    void viewerWithoutClientSessionIdIsRejectedBeforeItCanAffectPresence() throws Exception {
        LiveService liveService = mock(LiveService.class);
        LivePresenceService presenceService = mock(LivePresenceService.class);
        LiveStreamHandler handler = new LiveStreamHandler(liveService, presenceService);
        WebSocketSession session = mock(WebSocketSession.class);
        LiveRoom room = new LiveRoom();
        room.setId(100L);
        room.setStatus("LIVE");

        when(session.getId()).thenReturn("missing-presence-session");
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/live/100?role=viewer"));
        when(session.getAttributes()).thenReturn(Map.of("userId", 7L));
        when(liveService.getById(100L)).thenReturn(room);

        handler.afterConnectionEstablished(session);

        verify(session).close();
        verifyNoInteractions(presenceService);
        verify(liveService, never()).joinRoom(100L);
    }
}
