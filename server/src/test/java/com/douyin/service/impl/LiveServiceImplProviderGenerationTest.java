package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.douyin.config.LiveMediaProperties;
import com.douyin.engine.StreamingEngine;
import com.douyin.engine.StreamingSessionManager;
import com.douyin.entity.LiveRoom;
import com.douyin.mapper.LiveRoomMapper;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveServiceImplProviderGenerationTest {

    @Test
    void providerStartedAcceptsLegacyEmptyGenerationAndPersistsCurrentGeneration() {
        LiveRoomMapper mapper = mock(LiveRoomMapper.class);
        LiveRoom legacyRoom = legacyRoom();
        when(mapper.selectById(99L)).thenReturn(legacyRoom);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        LiveServiceImpl service = service(mapper);

        LiveRoom recovered = service.providerStarted(99L, "stream-99", "srs-restarted:publisher-1");

        assertNotNull(recovered);
        assertLegacyBlankCas(mapper);
    }

    @Test
    void providerUnavailableAlsoAcceptsLegacyEmptyGeneration() {
        LiveRoomMapper mapper = mock(LiveRoomMapper.class);
        when(mapper.selectById(99L)).thenReturn(legacyRoom());
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        LiveRoom degraded = service(mapper).providerUnavailable(99L, "stream-99");

        assertNotNull(degraded);
        assertLegacyBlankCas(mapper);
    }

    @Test
    void providerHeartbeatDoesNotTreatLegacyBlankAsCurrentGeneration() {
        LiveRoomMapper mapper = mock(LiveRoomMapper.class);
        when(mapper.selectById(99L)).thenReturn(legacyRoom());

        LiveRoom result = service(mapper).providerHeartbeat(99L, "stream-99", "srs-1:client-1");

        assertNull(result);
        verify(mapper, never()).update(isNull(), any(Wrapper.class));
    }

    private static LiveServiceImpl service(LiveRoomMapper mapper) {
        initializeTableMetadata();
        LiveServiceImpl service = new LiveServiceImpl(
                mock(StreamingSessionManager.class),
                mock(StreamingEngine.class),
                mock(LiveMediaProperties.class),
                false,
                45L);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        return service;
    }

    private static LiveRoom legacyRoom() {
        LiveRoom room = new LiveRoom();
        room.setId(99L);
        room.setStatus("LIVE");
        room.setSrtStreamId("stream-99");
        room.setProviderSessionId("");
        return room;
    }

    private static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "legacy-provider-generation-test"),
                LiveRoom.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void assertLegacyBlankCas(LiveRoomMapper mapper) {
        ArgumentCaptor<Wrapper<LiveRoom>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("provider_session_id"));
        assertTrue(sql.contains("or"), "legacy CAS must accept SQL NULL or empty-string generation");
    }
}
