package com.douyin.rtc;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.repository.RtcCallSessionMapper;
import com.douyin.rtc.service.CallSessionLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallSessionLookupServiceTest {

    @SuppressWarnings("unchecked")
    @Test
    void invalidIdNeverHitsMysqlOrRedis() {
        RtcCallSessionMapper mapper = mock(RtcCallSessionMapper.class);
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        CallSessionLookupService service = new CallSessionLookupService(mapper, redis);

        assertThatThrownBy(() -> service.find("../bad"))
                .isInstanceOf(CallDomainException.class)
                .satisfies(error -> assertThat(((CallDomainException) error).getCode())
                        .isEqualTo(CallErrorCode.INVALID_ARGUMENT));
        verify(mapper, times(0)).findByCallId(anyString());
    }

    @SuppressWarnings("unchecked")
    @Test
    void negativeCachePreventsRepeatedMysqlMiss() {
        RtcCallSessionMapper mapper = mock(RtcCallSessionMapper.class);
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null).thenReturn(null).thenReturn("missing");
        when(mapper.findByCallId("missing-call-0001")).thenReturn(null);
        CallSessionLookupService service = new CallSessionLookupService(mapper, redis);

        assertThat(service.find("missing-call-0001")).isNull();
        assertThat(service.find("missing-call-0001")).isNull();

        verify(mapper, times(1)).findByCallId("missing-call-0001");
    }
}
