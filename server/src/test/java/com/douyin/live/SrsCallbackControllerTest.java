package com.douyin.live;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.douyin.controller.SrsCallbackController;
import com.douyin.entity.LiveRoom;
import com.douyin.entity.LiveProviderSession;
import com.douyin.service.LiveMediaTokenService;
import com.douyin.service.LiveProviderSessionService;
import com.douyin.service.LiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SrsCallbackControllerTest {

    private static final String STREAM_KEY = "live-stream-key";
    private static final String CALLBACK_SECRET = "callback-secret-0123456789012345678901";
    private LiveService liveService;
    private LiveProviderSessionService sessionService;
    private final LiveMediaTokenService tokenService = new LiveMediaTokenService(
            true,
            "01234567890123456789012345678901",
            300,
            CALLBACK_SECRET,
            Clock.fixed(Instant.ofEpochSecond(1_700_000_000), ZoneOffset.UTC));
    private SrsCallbackController controller;
    private LiveRoom liveRoom;

    @BeforeEach
    void setUp() {
        liveService = mock(LiveService.class);
        sessionService = mock(LiveProviderSessionService.class);
        controller = new SrsCallbackController(liveService, tokenService, sessionService);
        liveRoom = new LiveRoom();
        liveRoom.setId(99L);
        liveRoom.setHostUserId(99L);
        liveRoom.setSrtStreamId(STREAM_KEY);
        liveRoom.setStatus("LIVE");
        liveRoom.setProviderState("STARTING");
        when(liveService.getOne(any(LambdaQueryWrapper.class))).thenReturn(liveRoom);
        when(liveService.providerStarted(anyLong(), anyString(), anyString())).thenReturn(liveRoom);
        when(sessionService.accept(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LiveProviderSession());
        when(sessionService.close(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
    }

    @Test
    void admitsSignedPublishCallback() {
        String token = tokenService.issue(
                liveRoom.getId(), STREAM_KEY, liveRoom.getHostUserId(), LiveMediaTokenService.Purpose.INGEST).value();
        Map<String, String> params = params(token, CALLBACK_SECRET);

        assertEquals(HttpStatus.OK, controller.onPublish(Map.of(), params).getStatusCode());
        verify(sessionService).accept(any(), any(), any(), any(), any(), any(), any(), any());
        verify(liveService).providerStarted(99L, STREAM_KEY, "srs-1:client-1");
    }

    @Test
    void rejectsWrongCallbackSecretAndWrongPurpose() {
        String playToken = tokenService.issue(
                liveRoom.getId(), STREAM_KEY, 7L, LiveMediaTokenService.Purpose.PLAY).value();

        assertEquals(HttpStatus.FORBIDDEN, controller.onPublish(Map.of(), params(playToken, "wrong"))
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.onPublish(Map.of(), params(playToken, CALLBACK_SECRET))
                .getStatusCode());
    }

    @Test
    void rejectsPublishTokenThatWasSignedForANonHost() {
        String ingestToken = tokenService.issue(
                liveRoom.getId(), STREAM_KEY, 7L, LiveMediaTokenService.Purpose.INGEST).value();

        assertEquals(HttpStatus.FORBIDDEN, controller.onPublish(Map.of(), params(ingestToken, CALLBACK_SECRET))
                .getStatusCode());
    }

    @Test
    void acceptsPlayTokenFromParamQueryAndStopsOnlyTheMatchingPlaySession() {
        String playToken = tokenService.issue(
                liveRoom.getId(), STREAM_KEY, 7L, LiveMediaTokenService.Purpose.PLAY).value();
        String publishToken = tokenService.issue(
                liveRoom.getId(), STREAM_KEY, liveRoom.getHostUserId(), LiveMediaTokenService.Purpose.INGEST).value();
        Map<String, String> playParams = params(null, CALLBACK_SECRET);
        playParams.put("param", "token=" + playToken);
        Map<String, String> stopParams = params(null, CALLBACK_SECRET);
        stopParams.put("param", "token=" + publishToken);

        assertEquals(HttpStatus.OK, controller.onPlay(Map.of(), playParams).getStatusCode());
        assertEquals(HttpStatus.OK, controller.onStop(Map.of(), stopParams)
                .getStatusCode());
        verify(sessionService).close(99L, "PLAY", "client-1", "srs-1", "srs-1:client-1", STREAM_KEY, "on_stop");
        verify(liveService, never()).providerHeartbeat(anyLong(), anyString(), anyString());
        assertEquals(HttpStatus.FORBIDDEN, controller.onStop(Map.of(), Map.of("callback_token", "wrong"))
                .getStatusCode());
    }

    @Test
    void playCallbackCannotReviveADegradedPublisher() {
        liveRoom.setStatus("DEGRADED");
        liveRoom.setProviderSessionId("srs-old:publisher-old");
        String playToken = tokenService.issue(
                liveRoom.getId(), STREAM_KEY, 7L, LiveMediaTokenService.Purpose.PLAY).value();

        assertEquals(HttpStatus.OK, controller.onPlay(Map.of(), params(playToken, CALLBACK_SECRET))
                .getStatusCode());
        verify(sessionService).accept(99L, "PLAY", "client-1", "srs-1", "srs-1:client-1",
                STREAM_KEY, 7L, "on_play");
        verify(liveService, never()).providerHeartbeat(anyLong(), anyString(), anyString());
        verify(liveService, never()).providerStarted(anyLong(), anyString(), anyString());
    }

    @Test
    void unpublishClosesPublishAndStartsBoundedProviderGrace() {
        Map<String, String> callback = params(null, CALLBACK_SECRET);

        assertEquals(HttpStatus.OK, controller.onUnpublish(Map.of(), callback).getStatusCode());
        verify(sessionService).close(99L, "PUBLISH", "client-1", "srs-1", "srs-1:client-1", STREAM_KEY,
                "on_unpublish");
        verify(liveService).providerDisconnected(99L, STREAM_KEY, "srs-1:client-1");
    }

    @Test
    void delayedPublishGenerationIsRejectedWithoutReplacingTheCurrentRoomSession() {
        String token = tokenService.issue(
                liveRoom.getId(), STREAM_KEY, liveRoom.getHostUserId(), LiveMediaTokenService.Purpose.INGEST).value();
        when(sessionService.accept(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);

        assertEquals(HttpStatus.CONFLICT, controller.onPublish(Map.of(), params(token, CALLBACK_SECRET)).getStatusCode());
        verify(liveService, never()).providerStarted(anyLong(), anyString(), anyString());
    }

    @Test
    void rejectsRoomThatIsNoLongerLive() {
        liveRoom.setStatus("ENDED");
        String token = tokenService.issue(
                liveRoom.getId(), STREAM_KEY, 7L, LiveMediaTokenService.Purpose.PLAY).value();

        assertEquals(HttpStatus.FORBIDDEN, controller.onPlay(Map.of(), params(token, CALLBACK_SECRET))
                .getStatusCode());
    }

    @Test
    void httpCallbacksRunInsideATransactionBoundary() throws NoSuchMethodException {
        assertTransactional("onPublish");
        assertTransactional("onPlay");
        assertTransactional("onUnpublish");
        assertTransactional("onStop");
    }

    private static void assertTransactional(String methodName) throws NoSuchMethodException {
        assertTrue(SrsCallbackController.class
                        .getMethod(methodName, MultiValueMap.class, String.class)
                        .isAnnotationPresent(Transactional.class),
                () -> methodName + " must keep the room mutation, provider session projection, and state transition atomic");
    }

    private static Map<String, String> params(String token, String callbackSecret) {
        Map<String, String> params = new HashMap<>();
        params.put("stream", STREAM_KEY);
        params.put("callback_token", callbackSecret);
        params.put("client_id", "client-1");
        params.put("server_id", "srs-1");
        if (token != null) params.put("token", token);
        return params;
    }
}
