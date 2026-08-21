package com.douyin.rtc.timeout;

import java.time.Instant;
import java.util.List;

/** Rebuildable realtime timeout index. MySQL remains the durable call fact. */
public interface CallTimeoutIndex {

    record DueTimeout(String callId, String expectedState, Instant expireAt) {}

    void schedule(String callId, String expectedState, Instant expireAt);

    void remove(String callId);

    List<DueTimeout> due(Instant now, int limit);
}
