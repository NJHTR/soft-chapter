package com.douyin.live;

import com.douyin.service.LivePresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class LivePresenceServiceTest {

    private RedisTemplate<String, Object> redis;
    private ZSetOperations<String, Object> zset;
    private LivePresenceService service;

    @BeforeEach
    void setUp() {
        redis = mock(RedisTemplate.class);
        zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        service = new LivePresenceService(redis);
    }

    @Test
    void touchAndLeaveAreIdempotentForExistingRedisMembers() {
        Map<String, Long> members = new ConcurrentHashMap<>();
        doAnswer(invocation -> {
            String member = invocation.getArgument(1);
            Double score = invocation.getArgument(2);
            Long previous = members.put(member, score.longValue());
            return previous == null;
        }).when(zset)
                .add(anyString(), anyString(), anyDouble());
        doAnswer(invocation -> {
            double min = invocation.getArgument(1);
            double max = invocation.getArgument(2);
            long before = members.size();
            members.entrySet().removeIf(entry -> entry.getValue() >= min && entry.getValue() <= max);
            return before - members.size();
        }).when(zset).removeRangeByScore(anyString(), anyDouble(), anyDouble());
        when(zset.zCard("douyin:live:presence:100")).thenAnswer(invocation -> (long) members.size());
        when(zset.remove("douyin:live:presence:100", "7:sess-1")).thenAnswer(invocation -> {
            boolean removed = members.remove("7:sess-1") != null;
            return removed ? 1L : 0L;
        });

        assertTrue(service.touch(100L, 7L, "sess-1"));
        assertFalse(service.touch(100L, 7L, "sess-1"));
        assertEquals(1, service.count(100L));
        assertTrue(service.leave(100L, 7L, "sess-1"));
        assertEquals(0, service.count(100L));
        assertFalse(service.leave(100L, 7L, "sess-1"));

        verify(zset, times(2)).add(eq("douyin:live:presence:100"), eq("7:sess-1"), anyDouble());
        verify(zset, times(2)).remove("douyin:live:presence:100", "7:sess-1");
    }

    @Test
    void failsClosedWhenRedisIsUnavailableInsteadOfReturningNodeLocalCounts() {
        when(redis.opsForZSet()).thenThrow(new RuntimeException("redis down"));

        assertFalse(service.touch(200L, 9L, "sess-a"));
        assertFalse(service.touch(200L, 9L, "sess-a"));
        assertEquals(0, service.count(200L));
        assertFalse(service.leave(200L, 9L, "sess-a"));
    }

    @Test
    void rejectsMissingOrInvalidSessionIdsBeforeTouchingRedis() {
        assertFalse(service.touch(300L, 10L, null));
        assertFalse(service.touch(300L, 10L, "   "));
        assertFalse(service.touch(300L, 10L, "x".repeat(129)));
        assertFalse(service.leave(300L, 10L, null));
        assertFalse(service.leave(300L, 10L, "   "));

        verify(redis, never()).opsForZSet();
        verifyNoInteractions(zset);
    }
}
