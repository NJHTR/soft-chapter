package com.douyin.rtc.support;

import com.douyin.entity.Message;
import com.douyin.rtc.domain.CallEvent;
import com.douyin.rtc.domain.CallParticipant;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.repository.RtcAclMapper;
import com.douyin.rtc.repository.RtcCallEventMapper;
import com.douyin.rtc.repository.RtcCallParticipantMapper;
import com.douyin.rtc.repository.RtcCallSessionMapper;
import com.douyin.rtc.repository.RtcMessageProjectionMapper;
import com.douyin.rtc.service.GroupMemberProfile;
import com.douyin.service.RedisCacheService;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 内存仓储夹具: 用 Mockito mock 包一层内存存储,忠实模拟
 * mapper 的守卫式 UPDATE 语义(COALESCE 保留历史值)与
 * INSERT IGNORE 幂等语义。纯 JUnit5,不启动 Spring。
 */
public final class RtcRepoFixture {

    public final RtcCallSessionMapper sessions = mock(RtcCallSessionMapper.class);
    public final RtcCallParticipantMapper participants = mock(RtcCallParticipantMapper.class);
    public final RtcCallEventMapper events = mock(RtcCallEventMapper.class);
    public final RtcAclMapper acl = mock(RtcAclMapper.class);
    public final RtcMessageProjectionMapper projection = mock(RtcMessageProjectionMapper.class);
    public final RedisCacheService redis = mock(RedisCacheService.class);

    public final Map<String, CallSession> sessionsByCall = new LinkedHashMap<>();
    public final Map<String, CallSession> sessionsByClientRequest = new HashMap<>();
    public final Map<String, CallParticipant> participantsByKey = new HashMap<>();
    public final Map<String, CallEvent> eventsById = new LinkedHashMap<>();
    public final List<CallEvent> eventLog = new ArrayList<>();
    public final List<Message> projections = new ArrayList<>();

    public final Set<String> follows = new HashSet<>();
    public final Map<Long, Set<Long>> groupMembersByGroup = new HashMap<>();

    public RtcRepoFixture() {
        wire();
    }

    private void wire() {
        // ===== 会话 =====
        when(sessions.insert(any(CallSession.class))).thenAnswer(inv -> {
            CallSession s = inv.getArgument(0);
            // uk_client_request_id 唯一语义: 冲突时 INSERT 抛 DuplicateKeyException(并发竞态路径)
            if (s.getClientRequestId() != null && sessionsByClientRequest.containsKey(s.getClientRequestId())) {
                throw new DuplicateKeyException("client_request_id 唯一冲突: " + s.getClientRequestId());
            }
            sessionsByCall.put(s.getCallId(), s);
            sessionsByClientRequest.put(s.getClientRequestId(), s);
            return 1;
        });
        when(sessions.findByCallId(anyString())).thenAnswer(inv -> sessionsByCall.get((String) inv.getArgument(0)));
        when(sessions.findByClientRequestId(anyString()))
                .thenAnswer(inv -> sessionsByClientRequest.get((String) inv.getArgument(0)));
        when(sessions.findActiveByUserId(anyLong())).thenAnswer(inv -> {
            Long userId = inv.getArgument(0);
            return sessionsByCall.values().stream()
                    .filter(s -> Set.of("RINGING", "ACCEPTED", "NEGOTIATING", "CONNECTED", "ENDING")
                            .contains(s.getState()))
                    .filter(s -> participantsByKey.values().stream().anyMatch(p ->
                            s.getCallId().equals(p.getCallId())
                                    && userId.equals(p.getUserId())
                                    && Set.of("INVITED", "RINGING", "JOINING", "CONNECTED", "RECONNECTING")
                                    .contains(p.getState())))
                    .findFirst()
                    .orElse(null);
        });
        when(sessions.findExpiredBefore(anyString(), any()))
                .thenAnswer(inv -> {
                    String state = inv.getArgument(0);
                    LocalDateTime now = inv.getArgument(1);
                    return sessionsByCall.values().stream()
                            .filter(s -> state.equals(s.getState()))
                            .filter(s -> s.getExpiresAt() != null && !s.getExpiresAt().isAfter(now))
                            .toList();
                });
        when(sessions.transitionSession(anyString(), anyString(), anyString(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    String callId = inv.getArgument(0);
                    String expected = inv.getArgument(1);
                    String target = inv.getArgument(2);
                    String endReason = inv.getArgument(3);
                    LocalDateTime endedAt = inv.getArgument(4);
                    LocalDateTime connectedAt = inv.getArgument(5);
                    LocalDateTime expiresAt = inv.getArgument(6);
                    CallSession s = sessionsByCall.get(callId);
                    if (s == null || !expected.equals(s.getState())) {
                        return 0;
                    }
                    s.setState(target);
                    s.setEndReason(endReason);
                    s.setEndedAt(endedAt);
                    if (connectedAt != null) {
                        s.setConnectedAt(connectedAt);
                    }
                    s.setExpiresAt(expiresAt);
                    return 1;
                });

        // ===== 参与者 =====
        when(participants.insert(any(CallParticipant.class))).thenAnswer(inv -> {
            CallParticipant p = inv.getArgument(0);
            participantsByKey.put(p.getCallId() + "|" + p.getUserId(), p);
            return 1;
        });
        when(participants.findByCallAndUser(anyString(), anyLong())).thenAnswer(inv -> {
            String callId = inv.getArgument(0);
            Long userId = inv.getArgument(1);
            return participantsByKey.get(callId + "|" + userId);
        });
        when(participants.listByCall(anyString())).thenAnswer(inv -> {
            String callId = inv.getArgument(0);
            return participantsByKey.values().stream()
                    .filter(p -> callId.equals(p.getCallId()))
                    .toList();
        });
        when(participants.transitionParticipant(anyString(), anyLong(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> {
                    String callId = inv.getArgument(0);
                    Long userId = inv.getArgument(1);
                    String expected = inv.getArgument(2);
                    String target = inv.getArgument(3);
                    String reason = inv.getArgument(4);
                    LocalDateTime joinedAt = inv.getArgument(5);
                    LocalDateTime leftAt = inv.getArgument(6);
                    CallParticipant p = participantsByKey.get(callId + "|" + userId);
                    if (p == null || !expected.equals(p.getState())) {
                        return 0;
                    }
                    p.setState(target);
                    if (reason != null) {
                        p.setReason(reason);
                    }
                    if (joinedAt != null) {
                        p.setJoinedAt(joinedAt);
                    }
                    if (leftAt != null) {
                        p.setLeftAt(leftAt);
                    }
                    return 1;
                });

        // ===== 事件账本 =====
        when(events.insertIgnore(any(CallEvent.class))).thenAnswer(inv -> {
            CallEvent e = inv.getArgument(0);
            if (eventsById.containsKey(e.getEventId())) {
                return 0;
            }
            eventsById.put(e.getEventId(), e);
            eventLog.add(e);
            return 1;
        });
        when(events.existsByEventId(anyString())).thenAnswer(inv -> eventsById.containsKey((String) inv.getArgument(0)));
        when(events.findByEventId(anyString())).thenAnswer(inv -> eventsById.get((String) inv.getArgument(0)));
        when(events.maxSeq(anyString(), anyLong())).thenAnswer(inv -> {
            String callId = inv.getArgument(0);
            Long pid = inv.getArgument(1);
            return eventLog.stream()
                    .filter(e -> callId.equals(e.getCallId()) && pid.equals(e.getParticipantId()))
                    .mapToLong(CallEvent::getSeq)
                    .max()
                    .orElse(0L);
        });
        when(events.listByCall(anyString())).thenAnswer(inv -> eventLog.stream()
                .filter(e -> inv.getArgument(0).equals(e.getCallId()))
                .sorted(Comparator.comparing(CallEvent::getSeq))
                .toList());
        when(events.listByCallAndParticipant(anyString(), anyLong())).thenAnswer(inv -> eventLog.stream()
                .filter(e -> inv.getArgument(0).equals(e.getCallId()))
                .filter(e -> inv.getArgument(1).equals(e.getParticipantId()))
                .sorted(Comparator.comparing(CallEvent::getSeq))
                .toList());

        // ===== ACL =====
        when(acl.mutualFollowConfirmed(anyLong(), anyLong())).thenAnswer(inv -> {
            Long a = inv.getArgument(0);
            Long b = inv.getArgument(1);
            return follows.contains(key(a, b)) && follows.contains(key(b, a)) ? 1 : 0;
        });
        when(acl.isGroupMember(anyLong(), anyLong())).thenAnswer(inv -> {
            Long groupId = inv.getArgument(0);
            Long userId = inv.getArgument(1);
            Set<Long> members = groupMembersByGroup.get(groupId);
            return members != null && members.contains(userId);
        });
        when(acl.listGroupMemberUserIds(anyLong())).thenAnswer(inv -> {
            Set<Long> members = groupMembersByGroup.get((Long) inv.getArgument(0));
            return members == null ? List.of() : new ArrayList<>(members);
        });
        when(acl.listGroupMemberProfiles(anyLong())).thenAnswer(inv -> {
            Set<Long> members = groupMembersByGroup.get((Long) inv.getArgument(0));
            if (members == null) return List.of();
            return members.stream()
                    .sorted()
                    .map(id -> new GroupMemberProfile(id, "user-" + id, "avatar-" + id))
                    .toList();
        });

        // ===== 投影 =====
        when(projection.insertProjection(any(Message.class))).thenAnswer(inv -> {
            projections.add(inv.getArgument(0));
            return 1;
        });
        when(projection.findCallProjections(anyString())).thenAnswer(inv -> {
            String callId = inv.getArgument(0);
            String marker = "\"call_id\":\"" + callId + "\"";
            return projections.stream()
                    .filter(m -> m.getExtra() != null && m.getExtra().contains(marker))
                    .toList();
        });

        // ===== 限流放行 =====
        when(redis.rateLimit(anyString(), anyString(), anyInt(), any())).thenReturn(true);
    }

    private static String key(Long a, Long b) {
        return a + "|" + b;
    }

    public void setMutualFollow(Long a, Long b) {
        follows.add(key(a, b));
        follows.add(key(b, a));
    }

    public void setFollow(Long followerId, Long followedId) {
        follows.add(key(followerId, followedId));
    }

    public void addGroupMember(Long groupId, Long userId) {
        groupMembersByGroup.computeIfAbsent(groupId, k -> new HashSet<>()).add(userId);
    }

    public CallSession session(String callId) {
        return sessionsByCall.get(callId);
    }

    public CallSession sessionByClientRequest(String clientRequestId) {
        return sessionsByClientRequest.get(clientRequestId);
    }

    public CallParticipant participant(String callId, Long userId) {
        return participantsByKey.get(callId + "|" + userId);
    }

    public List<CallParticipant> participantsOf(String callId) {
        return participantsByKey.values().stream()
                .filter(p -> callId.equals(p.getCallId()))
                .toList();
    }

    public List<CallEvent> eventsOf(String callId) {
        return eventLog.stream()
                .filter(e -> callId.equals(e.getCallId()))
                .sorted(Comparator.comparing(CallEvent::getSeq))
                .toList();
    }

    public long eventCount(String callId, String kind) {
        return eventsOf(callId).stream().filter(e -> kind.equals(e.getKind())).count();
    }

    public Duration constRingingTtl() {
        return Duration.ofSeconds(30);
    }

    public Duration constNegotiatingTtl() {
        return Duration.ofMinutes(5);
    }
}
