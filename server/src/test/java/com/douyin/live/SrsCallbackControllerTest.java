package com.douyin.live;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.douyin.controller.SrsCallbackController;
import com.douyin.entity.LiveRoom;
import com.douyin.service.LiveMediaTokenService;
import com.douyin.service.LiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SrsCallbackControllerTest {

    private static final String STREAM_KEY = "live-stream-key";
    private static final String CALLBACK_SECRET = "callback-secret";
    private final LiveService liveService = mock(LiveService.class);
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
        controller = new SrsCallbackController(liveService, tokenService);
        liveRoom = new LiveRoom();
        liveRoom.setId(99L);
        liveRoom.setSrtStreamId(STREAM_KEY);
        liveRoom.setStatus("LIVE");
        when(liveService.getOne(any(LambdaQueryWrapper.class))).thenReturn(liveRoom);
    }

    @Test
    void admitsSignedPublishCallback() {
        String token = tokenService.issue(
                liveRoom.getId(), STREAM_KEY, 7L, LiveMediaTokenService.Purpose.INGEST).value();
        Map<String, String> params = params(token, CALLBACK_SECRET);

        assertEquals(HttpStatus.OK, controller.onPublish(params).getStatusCode());
    }

    @Test
    void rejectsWrongCallbackSecretAndWrongPurpose() {
        String playToken = tokenService.issue(
                liveRoom.getId(), STREAM_KEY, 7L, LiveMediaTokenService.Purpose.PLAY).value();

        assertEquals(HttpStatus.FORBIDDEN, controller.onPublish(params(playToken, "wrong"))
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.onPublish(params(playToken, CALLBACK_SECRET))
                .getStatusCode());
    }

    @Test
    void rejectsRoomThatIsNoLongerLive() {
        liveRoom.setStatus("ENDED");
        String token = tokenService.issue(
                liveRoom.getId(), STREAM_KEY, 7L, LiveMediaTokenService.Purpose.PLAY).value();

        assertEquals(HttpStatus.FORBIDDEN, controller.onPlay(params(token, CALLBACK_SECRET))
                .getStatusCode());
    }

    private static Map<String, String> params(String token, String callbackSecret) {
        Map<String, String> params = new HashMap<>();
        params.put("stream", STREAM_KEY);
        params.put("token", token);
        params.put("callback_token", callbackSecret);
        return params;
    }
}
