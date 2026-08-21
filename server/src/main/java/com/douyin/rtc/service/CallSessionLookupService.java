package com.douyin.rtc.service;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.repository.RtcCallSessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/** Negative-cache and single-flight protection for untrusted call-id lookups. */
@Slf4j
@Service
public class CallSessionLookupService {

    private static final Pattern CALL_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final String NEGATIVE_PREFIX = "douyin:rtc:call:negative:";
    private static final String NEGATIVE_VALUE = "missing";

    private final RtcCallSessionMapper mapper;
    private final RedisTemplate<String, Object> redis;
    private final ConcurrentHashMap<String, Object> flights = new ConcurrentHashMap<>();

    public CallSessionLookupService(RtcCallSessionMapper mapper, RedisTemplate<String, Object> redis) {
        this.mapper = mapper;
        this.redis = redis;
    }

    public CallSession find(String callId) {
        validate(callId);
        if (negativeHit(callId)) {
            return null;
        }
        Object lock = flights.computeIfAbsent(callId, ignored -> new Object());
        synchronized (lock) {
            try {
                if (negativeHit(callId)) {
                    return null;
                }
                CallSession found = mapper.findByCallId(callId);
                if (found == null) {
                    rememberMissing(callId);
                }
                return found;
            } finally {
                flights.remove(callId, lock);
            }
        }
    }

    public void invalidate(String callId) {
        if (callId == null) {
            return;
        }
        try {
            redis.delete(NEGATIVE_PREFIX + callId);
        } catch (Exception e) {
            log.debug("Call negative-cache invalidate failed: {}", e.getMessage());
        }
    }

    private void validate(String callId) {
        if (callId == null || !CALL_ID.matcher(callId).matches()) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "call_id 格式非法");
        }
    }

    private boolean negativeHit(String callId) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(NEGATIVE_PREFIX + callId)).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    private void rememberMissing(String callId) {
        try {
            long ttlSeconds = ThreadLocalRandom.current().nextLong(20L, 41L);
            redis.opsForValue().set(NEGATIVE_PREFIX + callId, NEGATIVE_VALUE, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.debug("Call negative-cache write failed: {}", e.getMessage());
        }
    }
}
