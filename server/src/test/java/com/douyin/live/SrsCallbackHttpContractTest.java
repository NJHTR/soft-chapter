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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SrsCallbackHttpContractTest {

    private static final String STREAM_KEY = "live-stream-key";
    private static final String CALLBACK_SECRET = "callback-secret-0123456789012345678901";
    private LiveService liveService;
    private LiveMediaTokenService tokenService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        liveService = mock(LiveService.class);
        LiveProviderSessionService sessionService = mock(LiveProviderSessionService.class);
        tokenService = new LiveMediaTokenService(
                true,
                "01234567890123456789012345678901",
                300,
                CALLBACK_SECRET,
                Clock.fixed(Instant.ofEpochSecond(1_700_000_000), ZoneOffset.UTC));
        LiveRoom room = new LiveRoom();
        room.setId(99L);
        room.setHostUserId(99L);
        room.setSrtStreamId(STREAM_KEY);
        room.setStatus("STARTING");
        when(liveService.getOne(any(LambdaQueryWrapper.class))).thenReturn(room);
        when(liveService.providerStarted(any(), any(), any())).thenReturn(room);
        when(sessionService.accept(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LiveProviderSession());
        mvc = MockMvcBuilders.standaloneSetup(new SrsCallbackController(liveService, tokenService, sessionService))
                .build();
    }

    @Test
    void acceptsFormEncodedPublishCallback() throws Exception {
        String token = tokenService.issue(99L, STREAM_KEY, 99L, LiveMediaTokenService.Purpose.INGEST).value();

        mvc.perform(post("/api/live/provider/srs/on_publish")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("callback_token", CALLBACK_SECRET)
                        .param("server_id", "srs-1")
                        .param("client_id", "client-1")
                        .param("stream", STREAM_KEY)
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }

    @Test
    void acceptsJsonPlayCallback() throws Exception {
        String token = tokenService.issue(99L, STREAM_KEY, 7L, LiveMediaTokenService.Purpose.PLAY).value();
        String payload = "{" +
                "\"callback_token\":\"" + CALLBACK_SECRET + "\"," +
                "\"server_id\":\"srs-1\"," +
                "\"client_id\":\"client-1\"," +
                "\"stream\":\"" + STREAM_KEY + "\"," +
                "\"token\":\"" + token + "\"}";

        mvc.perform(post("/api/live/provider/srs/on_play")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }
}
