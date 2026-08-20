package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.douyin.config.LiveMediaProperties;
import com.douyin.engine.StreamingEngine;
import com.douyin.engine.StreamingSessionManager;
import com.douyin.entity.LiveRoom;
import com.douyin.mapper.LiveRoomMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
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

/**
 * RTC-006 ENDING 状态机边界：主播要求的关闭拥有有界 ENDING 窗口，provider
 * 回调/reconciliation 不得把它改写成 DEGRADED 或提前结束；只有精确 generation
 * 的关闭事件或可到达 provider 确认流缺失并越过 grace 后才能事务化终态。
 */
class LiveServiceImplEndingStateTest {

    @Test
    void endLiveOnPreviewEndsImmediatelyWithoutProviderGeneration() {
        LiveRoomMapper mapper = mock(LiveRoomMapper.class);
        LiveRoom preview = room("PREVIEW", "stream-9", null);
        LiveRoom ended = room("ENDED", "stream-9", null);
        when(mapper.selectById(9L)).thenReturn(preview, ended);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        LiveRoom result = service(mapper).endLive(9L, 7L);

        assertNotNull(result);
        assertEndingTerminalCas(mapper, true);
        assertTrue(valuesContains(mapper, "PREVIEW"), "PREVIEW close must CAS the PREVIEW status");
        assertTrue(valuesContains(mapper, "ENDED"), "PREVIEW close must set ENDED terminal state");
        assertTrue(!valuesContains(mapper, "ENDING"),
                "PREVIEW close must not enter the provider ENDING window");
    }

    @Test
    void endLiveOnActiveRoomEntersEndingWithGenerationCas() {
        LiveRoomMapper mapper = mock(LiveRoomMapper.class);
        LiveRoom live = room("LIVE", "stream-9", "srs-1:client-1");
        LiveRoom ending = room("ENDING", "stream-9", "srs-1:client-1");
        when(mapper.selectById(9L)).thenReturn(live, ending);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        LiveRoom result = service(mapper).endLive(9L, 7L);

        assertNotNull(result);
        assertEndingTerminalCas(mapper, false);
        assertTrue(valuesContains(mapper, "srs-1:client-1"),
                "ending transition must carry the exact provider generation into the CAS");
        assertTrue(valuesContains(mapper, "LIVE"), "ending transition must CAS a mutable status");
        assertTrue(valuesContains(mapper, "ENDING"), "active rooms must enter ENDING before ENDED");
    }

    @Test
    void endLiveOnEndingOrEndedIsIdempotent() {
        LiveRoomMapper mapper = mock(LiveRoomMapper.class);
        LiveRoom ending = room("ENDING", "stream-9", "srs-1:client-1");
        when(mapper.selectById(9L)).thenReturn(ending);

        LiveRoom first = service(mapper).endLive(9L, 7L);
        LiveRoom second = service(mapper).endLive(9L, 7L);

        assertNotNull(first);
        assertNotNull(second);
        verify(mapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void endLiveWithReplacedProviderGenerationIsANoOp() {
        LiveRoomMapper mapper = mock(LiveRoomMapper.class);
        LiveRoom live = room("LIVE", "stream-9", "srs-1:old-client");
        LiveRoom current = room("LIVE", "stream-9", "srs-2:new-client");
        when(mapper.selectById(9L)).thenReturn(live, current);
        // The generation CAS misses because a replacement publisher won the
        // room; the end request must not stop that publisher's session.
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        LiveRoom result = service(mapper).endLive(9L, 7L);

        assertNotNull(result);
        assertEndingTerminalCas(mapper, false);
        assertTrue(valuesContains(mapper, "srs-1:old-client"),
                "delayed end must still guard the read-generation");
        assertTrue(valuesContains(mapper, "LIVE"));
    }

    @Test
    void endLiveWithUnknownStatusReturnsNull() {
        LiveRoomMapper mapper = mock(LiveRoomMapper.class);
        when(mapper.selectById(9L)).thenReturn(room("ARCHIVED", "stream-9", null));

        assertNull(service(mapper).endLive(9L, 7L));
        verify(mapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void providerStartedRejectsEndingRoom() {
        LiveRoomMapper mapper = mock(LiveRoomMapper.class);
        when(mapper.selectById(9L)).thenReturn(room("ENDING", "stream-9", "srs-1:client-1"));

        assertNull(service(mapper).providerStarted(9L, "stream-9", "srs-2:client-2"));
        verify(mapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void providerHeartbeatRejectsEndingRoom() {
        LiveRoomMapper mapper = mock(LiveRoomMapper.class);
        when(mapper.selectById(9L)).thenReturn(room("ENDING", "stream-9", "srs-1:client-1"));

        assertNull(service(mapper).providerHeartbeat(9L, "stream-9", "srs-1:client-1"));
        verify(mapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void providerUnavailablePreservesEndingWithoutDegrading() {
        LiveRoomMapper mapper = mock(LiveRoomMapper.class);
        when(mapper.selectById(9L)).thenReturn(room("ENDING", "stream-9", "srs-1:client-1"));

        LiveRoom result = service(mapper).providerUnavailable(9L, "stream-9");

        assertNotNull(result);
        verify(mapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void providerDisconnectedInEndingClosesExactlyItsGeneration() {
        LiveRoomMapper mapper = mock(LiveRoomMapper.class);
        LiveRoom ending = room("ENDING", "stream-9", "srs-1:client-1");
        LiveRoom ended = room("ENDED", "stream-9", "srs-1:client-1");
        when(mapper.selectById(9L)).thenReturn(ending, ended);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        LiveRoom result = service(mapper)
                .providerDisconnected(9L, "stream-9", "srs-1:client-1");

        assertNotNull(result);
        assertEndingTerminalCas(mapper, false);
        assertTrue(valuesContains(mapper, "srs-1:client-1"),
                "ENDING close must CAS the exact provider generation");
        assertTrue(valuesContains(mapper, "ENDED"), "close event must set ENDED terminal state");
    }

    @Test
    void providerDisconnectedInEndingWithWrongGenerationIsRejected() {
        LiveRoomMapper mapper = mock(LiveRoomMapper.class);
        when(mapper.selectById(9L)).thenReturn(room("ENDING", "stream-9", "srs-1:client-1"));

        assertNull(service(mapper)
                .providerDisconnected(9L, "stream-9", "srs-2:other-client"));
        verify(mapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void endProviderRoomAcceptsEndingStatusForTerminalConvergence() {
        LiveRoomMapper mapper = mock(LiveRoomMapper.class);
        LiveRoom ending = room("ENDING", "stream-9", "srs-1:client-1");
        LiveRoom ended = room("ENDED", "stream-9", "srs-1:client-1");
        when(mapper.selectById(9L)).thenReturn(ending, ended);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        LiveRoom result = service(mapper)
                .endProviderRoom(9L, "stream-9", "srs-1:client-1", "provider_missing");

        assertNotNull(result);
        assertTrue(valuesContains(mapper, "ENDING"),
                "convergence must keep ENDING in the mutable status set, got " + params(mapper)
                        + " sql=" + sql(mapper));
        assertTrue(valuesContains(mapper, "ENDED"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listProviderRoomsIncludesEndingRooms() {
        LiveRoomMapper mapper = mock(LiveRoomMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        service(mapper).listProviderRooms();

        ArgumentCaptor<Wrapper<LiveRoom>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("status"), "provider room query must filter by status");
        List<Object> values = ((AbstractWrapper<LiveRoom, ?, ?>)
                wrapper.getValue()).getParamNameValuePairs().values().stream().toList();
        assertTrue(values.contains("ENDING"), "provider room query must include ENDING rooms");
        assertTrue(values.contains("STARTING") && values.contains("LIVE") && values.contains("DEGRADED"));
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

    private static LiveRoom room(String status, String streamKey, String providerSessionId) {
        LiveRoom room = new LiveRoom();
        room.setId(9L);
        room.setHostUserId(7L);
        room.setStatus(status);
        room.setSrtStreamId(streamKey);
        room.setProviderSessionId(providerSessionId);
        return room;
    }

    private static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "ending-state-test"),
                LiveRoom.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void assertEndingTerminalCas(LiveRoomMapper mapper, boolean terminal) {
        String expected = terminal ? "ENDED" : "ENDING";
        java.util.Map<String, Object> values = params(mapper);
        ArgumentCaptor<Wrapper<LiveRoom>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("id"), "room transitions must stay room-bound");
        assertTrue(values.values().contains(expected),
                "expected terminal state " + expected + " in CAS params, got " + values);
    }

    private static boolean valuesContains(LiveRoomMapper mapper, Object expected) {
        return params(mapper).values().stream().anyMatch(expected::equals);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String sql(LiveRoomMapper mapper) {
        return wrapper(mapper).getSqlSegment();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static java.util.Map<String, Object> params(LiveRoomMapper mapper) {
        ArgumentCaptor<Wrapper<LiveRoom>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), wrapper.capture());
        // getSqlSegment() lazily materializes WHERE parameters into the map.
        wrapper.getValue().getSqlSegment();
        return ((AbstractWrapper<LiveRoom, ?, ?>) wrapper.getValue()).getParamNameValuePairs();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Wrapper<LiveRoom> wrapper(LiveRoomMapper mapper) {
        ArgumentCaptor<Wrapper<LiveRoom>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), wrapper.capture());
        return wrapper.getValue();
    }
}