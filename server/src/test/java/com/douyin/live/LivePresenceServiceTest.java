package com.douyin.live;

import com.douyin.service.LivePresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class LivePresenceServiceTest {

    private RedisTemplate<String, Object> redis;
    private LivePresenceService service;

    @BeforeEach
    void setUp() {
        redis = mock(RedisTemplate.class);
        service = new LivePresenceService(redis);
    }

    @Test
    void touchLeaveAndCountUseAtomicScriptsAndRemoveEmptyPresenceKey() {
        Map<String, Long> members = new ConcurrentHashMap<>();
        doAnswer(invocation -> {
            RedisScript<?> script = invocation.getArgument(0);
            String source = script.getScriptAsString();
            if (source.contains("ZADD")) {
                String member = invocation.getArgument(6);
                return members.put(member, Long.parseLong(invocation.getArgument(5))) == null ? "1" : "0";
            }
            if (source.contains("redis.call('ZREM',")) {
                return members.remove(invocation.getArgument(5)) == null ? "0" : "1";
            }
            return String.valueOf(members.size());
        }).when(redis).execute(any(RedisScript.class), any(RedisSerializer.class), any(RedisSerializer.class),
                anyList(), any(Object[].class));

        assertTrue(service.touch(100L, 7L, "sess-1"));
        assertFalse(service.touch(100L, 7L, "sess-1"));
        assertEquals(1, service.count(100L));
        assertTrue(service.leave(100L, 7L, "sess-1"));
        assertEquals(0, service.count(100L));
        assertFalse(service.leave(100L, 7L, "sess-1"));

        verify(redis, times(6)).execute(any(RedisScript.class), any(RedisSerializer.class), any(RedisSerializer.class),
                anyList(), any(Object[].class));
    }

    @Test
    void failsClosedWhenRedisIsUnavailableInsteadOfReturningNodeLocalCounts() {
        doAnswer(invocation -> { throw new RuntimeException("redis down"); })
                .when(redis).execute(any(RedisScript.class), any(RedisSerializer.class), any(RedisSerializer.class),
                        anyList(), any(Object[].class));

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

        verifyNoInteractions(redis);
    }
}
