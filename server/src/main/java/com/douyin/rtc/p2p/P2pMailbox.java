package com.douyin.rtc.p2p;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单实例内存信令邮箱(有界、TTL)。原始 SDP/ICE 只在此瞬态缓冲,
 * 不写 Kafka/DB/聊天 WS/日志;持久化仅审计元数据与摘要。
 */
public class P2pMailbox {

    private static final class Entry {
        final P2pSignalingEnvelope envelope;
        final long storedEpochMs;

        Entry(P2pSignalingEnvelope envelope, long storedEpochMs) {
            this.envelope = envelope;
            this.storedEpochMs = storedEpochMs;
        }
    }

    private final Map<String, Deque<Entry>> boxes = new ConcurrentHashMap<>();
    private final int maxEntries;
    private final long ttlMs;
    private final Map<String, Long> lastSeq = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> seenEventIds = new ConcurrentHashMap<>();
    private final Map<String, long[]> rateWindows = new ConcurrentHashMap<>();

    public P2pMailbox(int maxEntries, long ttlMs) {
        this.maxEntries = maxEntries;
        this.ttlMs = ttlMs;
    }

    private static String boxKey(String callId, Long userId) {
        return callId + "|" + userId;
    }

    private static String seqKey(String callId, Long fromUserId) {
        return callId + "|" + fromUserId;
    }

    /**
     * 有界入箱:校验 seq 单调递增、event_id 幂等、速率上限;通过后写入 toUserId 邮箱。
     * 返回投递 seq。
     */
    public long put(P2pSignalingEnvelope env, long nowEpochMs, int ratePerSecond) {
        String sk = seqKey(env.getCallId(), env.getFromUserId());
        Map<String, Long> seen = seenEventIds.computeIfAbsent(sk, k -> new HashMap<>());
        Long seenSeq = seen.get(env.getEventId());
        if (seenSeq != null) {
            if (seenSeq == env.getSeq()) {
                return seenSeq;
            }
            throw new CallDomainException(CallErrorCode.EVENT_DUPLICATE, "event_id 与已消费语义不一致");
        }
        Long previous = lastSeq.get(sk);
        if (previous != null && env.getSeq() <= previous) {
            throw new CallDomainException(CallErrorCode.SEQ_OUT_OF_ORDER,
                    "seq " + env.getSeq() + " <= last " + previous);
        }
        long[] window = rateWindows.computeIfAbsent(sk, k -> new long[]{nowEpochMs, 0});
        if (nowEpochMs - window[0] >= 1000L) {
            window[0] = nowEpochMs;
            window[1] = 0;
        }
        if (window[1] >= ratePerSecond) {
            throw new CallDomainException(CallErrorCode.RATE_LIMITED, "信令速率超限");
        }
        window[1]++;
        Deque<Entry> box = boxes.computeIfAbsent(boxKey(env.getCallId(), env.getToUserId()), k -> new ArrayDeque<>());
        synchronized (box) {
            while (box.size() >= maxEntries) {
                box.pollFirst();
            }
            box.addLast(new Entry(env, nowEpochMs));
        }
        lastSeq.put(sk, env.getSeq());
        if (seen.size() < 64) {
            seen.put(env.getEventId(), env.getSeq());
        }
        return env.getSeq();
    }

    /** 取走收件箱全部未过期条目(清空缓冲,不落库)。 */
    public List<P2pSignalingEnvelope> take(String callId, Long userId, long nowEpochMs) {
        Deque<Entry> box = boxes.remove(boxKey(callId, userId));
        if (box == null) {
            return List.of();
        }
        List<P2pSignalingEnvelope> out = new ArrayList<>();
        synchronized (box) {
            while (!box.isEmpty()) {
                Entry e = box.pollFirst();
                if (nowEpochMs - e.storedEpochMs <= ttlMs) {
                    out.add(e.envelope);
                }
            }
        }
        return out;
    }

    public void removeCall(String callId) {
        for (Map.Entry<String, Deque<Entry>> e : boxes.entrySet()) {
            if (e.getKey().startsWith(callId + "|")) {
                boxes.remove(e.getKey());
            }
        }
        lastSeq.entrySet().removeIf(e -> e.getKey().startsWith(callId + "|"));
        seenEventIds.keySet().removeIf(k -> k.startsWith(callId + "|"));
        rateWindows.keySet().removeIf(k -> k.startsWith(callId + "|"));
    }
}