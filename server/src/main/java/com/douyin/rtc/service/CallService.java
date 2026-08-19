package com.douyin.rtc.service;

import com.douyin.rtc.domain.CallCommand;
import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallEndReason;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.domain.CallEvent;
import com.douyin.rtc.domain.CallEventKind;
import com.douyin.rtc.domain.CallParticipant;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.domain.CallState;
import com.douyin.rtc.domain.CallStateMachine;
import com.douyin.rtc.domain.ParticipantCommand;
import com.douyin.rtc.domain.ParticipantState;
import com.douyin.rtc.domain.ParticipantStateMachine;
import com.douyin.rtc.repository.RtcCallEventMapper;
import com.douyin.rtc.repository.RtcCallParticipantMapper;
import com.douyin.rtc.repository.RtcCallSessionMapper;
import com.douyin.service.RedisCacheService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 通话控制面命令编排 (rtc-domain 生命周期)。
 *
 * 幂等与乱序语义:
 * - 创建: client_request_id 幂等,重复返回原结果(不新建记录);
 * - 命令: event_id 全局唯一,相同 event_id 重放返回当前状态;
 * - 终态: 新命令返回当前状态,不创建新记录;
 * - 乱序: 转移表未列出的命令安全返回当前状态,不做隐式修正;
 * - 过期: RINGING 超过 expires_at 的命令抛 CALL_EXPIRED,由 TTL worker 收敛。
 */
@Slf4j
@Service
public class CallService {

    private static final long SYSTEM_PARTICIPANT = 0L;
    private static final String SYSTEM_EVENT_PREFIX = "sys:";
    private static final String TTL_EVENT_PREFIX = "ttl:";
    private static final String RATE_LIMIT_CREATE = "call:create";
    private static final String RATE_LIMIT_ACCEPT = "call:accept";
    private static final int RATE_LIMIT_CREATE_MAX = 20;
    private static final int RATE_LIMIT_ACCEPT_MAX = 60;
    /** RTC-005 首期房间上限(包含发起者)。 */
    private static final int GROUP_MAX_PARTICIPANTS = 8;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper();

    private final RtcCallSessionMapper sessionMapper;
    private final RtcCallParticipantMapper participantMapper;
    private final RtcCallEventMapper eventMapper;
    private final AclService aclService;
    private final CallLedgerService ledgerService;
    private final RedisCacheService redisCacheService;
    private final Duration ringingTtl;
    private final Duration negotiatingTtl;

    public CallService(RtcCallSessionMapper sessionMapper,
                       RtcCallParticipantMapper participantMapper,
                       RtcCallEventMapper eventMapper,
                       AclService aclService,
                       CallLedgerService ledgerService,
                       RedisCacheService redisCacheService,
                       @Value("${rtc.call.ringing-ttl:30s}") Duration ringingTtl,
                       @Value("${rtc.call.negotiating-ttl:5m}") Duration negotiatingTtl) {
        this.sessionMapper = sessionMapper;
        this.participantMapper = participantMapper;
        this.eventMapper = eventMapper;
        this.aclService = aclService;
        this.ledgerService = ledgerService;
        this.redisCacheService = redisCacheService;
        this.ringingTtl = ringingTtl;
        this.negotiatingTtl = negotiatingTtl;
    }

    // ==================== 创建 ====================

    /**
     * 创建通话。client_request_id 幂等: 已存在且发起者一致时返回原结果。
     * CREATED -> RINGING,所有参与者 INVITED -> RINGING。
     */
    public CallSession createCall(CreateCallCommand cmd) {
        if (cmd == null) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "缺少创建参数");
        }
        aclService.assertAuthenticated(cmd.initiatorId());
        Long initiator = cmd.initiatorId();
        String scope = cmd.scope();
        if (scope == null || !(CallScopeCodes.DIRECT.equals(scope) || CallScopeCodes.GROUP.equals(scope))) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "scope 仅支持 direct/group");
        }
        List<Long> targets = new ArrayList<>();
        Map<Long, GroupMemberProfile> groupProfiles = new LinkedHashMap<>();
        if (CallScopeCodes.DIRECT.equals(scope)) {
            aclService.assertDirectCallAllowed(initiator, cmd.targetUserId());
            targets.add(cmd.targetUserId());
        } else {
            aclService.assertGroupCallAllowed(initiator, cmd.groupId());
            List<GroupMemberProfile> profiles = aclService.groupMemberProfiles(cmd.groupId());
            if (profiles != null) {
                for (GroupMemberProfile profile : profiles) {
                    if (profile != null && profile.getUserId() != null) {
                        groupProfiles.putIfAbsent(profile.getUserId(), profile);
                    }
                }
            }
            // 兼容未升级的 ACL mapper；生产路径优先使用带展示快照的查询。
            if (groupProfiles.isEmpty()) {
                List<Long> members = aclService.groupMembers(cmd.groupId());
                if (members != null) {
                    for (Long member : members) {
                        if (member != null) {
                            groupProfiles.putIfAbsent(member, new GroupMemberProfile(member, null, null));
                        }
                    }
                }
            }
            if (groupProfiles.size() > GROUP_MAX_PARTICIPANTS) {
                throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT,
                        "群通话首期最多支持 " + GROUP_MAX_PARTICIPANTS + " 人");
            }
            for (Long member : groupProfiles.keySet()) {
                if (!member.equals(initiator)) {
                    targets.add(member);
                }
            }
            if (targets.isEmpty()) {
                throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "群内没有可呼叫的成员");
            }
        }
        if (cmd.clientRequestId() == null || cmd.clientRequestId().length() < 8) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "client_request_id 至少 8 位");
        }
        assertClientEventId(cmd.eventId());
        if (!rateLimitAllowed(RATE_LIMIT_CREATE, initiator)) {
            throw new CallDomainException(CallErrorCode.RATE_LIMITED, "发起通话过于频繁,请稍后再试");
        }

        // 幂等: 相同 client_request_id 已存在 → 同发起者返回原结果
        CallSession existing = sessionMapper.findByClientRequestId(cmd.clientRequestId());
        if (existing != null) {
            if (!existing.getInitiatorId().equals(initiator)) {
                throw new CallDomainException(CallErrorCode.SESSION_ALREADY_EXISTS,
                        "client_request_id 已被其他用户占用");
            }
            log.info("[CALL] create 幂等重放: callId={} clientRequestId={}", existing.getCallId(), cmd.clientRequestId());
            return existing;
        }

        // 建呼前的忙线守卫。幂等回放在此之前返回，避免同一个请求被自己的
        // 已有会话误判；真正的新会话则要求发起者和所有目标都可用。
        assertAvailableForCall(initiator);
        for (Long target : targets) {
            assertAvailableForCall(target);
        }

        String callId = UUID.randomUUID().toString().replace("-", "");
        CallSession session = new CallSession();
        session.setCallId(callId);
        session.setScope(scope);
        session.setMode(cmd.mode() != null ? cmd.mode() : "audio");
        session.setInitiatorId(initiator);
        session.setProvider(cmd.provider() != null ? cmd.provider() : "livekit");
        session.setState(CallState.CREATED.name());
        session.setClientRequestId(cmd.clientRequestId());
        session.setTraceId(cmd.traceId());
        try {
            sessionMapper.insert(session);
        } catch (DuplicateKeyException e) {
            // 并发下预查后插入竞态: uk_client_request_id 唯一冲突,回查返回原结果(幂等)
            CallSession raced = sessionMapper.findByClientRequestId(cmd.clientRequestId());
            if (raced == null) {
                throw e;
            }
            if (!raced.getInitiatorId().equals(initiator)) {
                throw new CallDomainException(CallErrorCode.SESSION_ALREADY_EXISTS,
                        "client_request_id 已被其他用户占用");
            }
            log.info("[CALL] create 唯一冲突回查: callId={} clientRequestId={}", raced.getCallId(), cmd.clientRequestId());
            return raced;
        }

        // CREATED -> RINGING (转移表第一行),RINGING TTL 开始计时
        String eventId = cmd.eventId() != null ? cmd.eventId() : systemEventId();
        LocalDateTime now = LocalDateTime.now();
        if (applySessionTransition(session, CallCommand.CREATE, null, now.plus(ringingTtl), null, null)) {
            // 参与者: 发起者 + 目标,全部 INVITED -> RINGING
            GroupMemberProfile initiatorProfile = groupProfiles.get(initiator);
            insertParticipant(callId, initiator, "initiator", initiatorProfile);
            for (Long target : targets) {
                insertParticipant(callId, target, "member", groupProfiles.get(target));
            }
            ringParticipants(session, initiator, targets);
            append(eventId, session, initiator, CallEventKind.CALL_REQUEST,
                    Map.of("scope", scope, "mode", session.getMode(), "targets", targets.size()), cmd.traceId());
            project(session, 0, 0);
        }
        return session;
    }

    // ==================== 生命周期命令 ====================

    /**
     * 被叫方接听。
     *
     * <p>direct 保持原有 RINGING -> ACCEPTED 语义；group 的 session 状态只由
     * 第一个接听者推进，之后每个成员独立执行 RINGING -> JOINING，不能因为
     * 其他成员已经接听而被乱序短路。</p>
     */
    public CallSession acceptCall(String callId, Long actorId, String eventId, String traceId) {
        assertClientEventId(eventId);
        CallSession session = requireSession(callId);
        aclService.assertAuthenticated(actorId);
        CallParticipant participant = requireTargetParticipant(session, actorId);
        if (isGroup(session)) {
            return acceptGroupCall(session, participant, actorId, eventId, traceId);
        }
        CallSession shortCircuit = replayOrTerminalOrOutOfOrder(session, CallCommand.ACCEPT, eventId);
        if (shortCircuit != null) {
            return shortCircuit;
        }
        if (isExpired(session)) {
            throw new CallDomainException(CallErrorCode.CALL_EXPIRED, "通话已过期,无法接听");
        }
        if (!rateLimitAllowed(RATE_LIMIT_ACCEPT, actorId)) {
            throw new CallDomainException(CallErrorCode.RATE_LIMITED, "操作过于频繁,请稍后再试");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!applySessionTransition(session, CallCommand.ACCEPT, null, null, null, null)) {
            return reload(session);
        }
        applyParticipantTransition(session, actorId, ParticipantState.RINGING, ParticipantCommand.JOIN, null, now, null);
        append(eventId, session, actorId, CallEventKind.CALL_ACCEPT, Map.of("mode", session.getMode()), traceId);
        project(session, 1, 0);
        return session;
    }

    /**
     * 被叫方拒绝。direct 拒绝会结束整场；group 只将当前成员置为 REJECTED，
     * 其余成员和 LiveKit room 不受影响。
     */
    public CallSession rejectCall(String callId, Long actorId, String eventId, String traceId) {
        assertClientEventId(eventId);
        CallSession session = requireSession(callId);
        aclService.assertAuthenticated(actorId);
        CallParticipant participant = requireTargetParticipant(session, actorId);
        if (isGroup(session)) {
            return rejectGroupCall(session, participant, actorId, eventId, traceId);
        }
        CallSession shortCircuit = replayOrTerminalOrOutOfOrder(session, CallCommand.REJECT, eventId);
        if (shortCircuit != null) {
            return shortCircuit;
        }
        if (isExpired(session)) {
            throw new CallDomainException(CallErrorCode.CALL_EXPIRED, "通话已过期,无法拒绝");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!applySessionTransition(session, CallCommand.REJECT, CallEndReason.REJECTED.name(), null, null, now)) {
            return reload(session);
        }
        applyParticipantTransition(session, actorId, ParticipantState.RINGING, ParticipantCommand.REJECT,
                CallEndReason.REJECTED.name(), null, now);
        append(eventId, session, actorId, CallEventKind.CALL_REJECT, Map.of("mode", session.getMode()), traceId);
        project(session, 0, 0);
        return session;
    }

    private CallSession acceptGroupCall(CallSession session, CallParticipant participant,
                                        Long actorId, String eventId, String traceId) {
        CallState state = CallState.valueOf(session.getState());
        if (ledgerService.isReplay(eventId) || CallStateMachine.isFinal(state)) {
            return session;
        }
        // 同一成员重复接听、已拒绝或已离开均是幂等/乱序，不改变其他成员。
        if (!ParticipantState.RINGING.name().equals(participant.getState())) {
            return session;
        }
        if (isExpired(session)) {
            throw new CallDomainException(CallErrorCode.CALL_EXPIRED, "通话已过期,无法接听");
        }
        if (!rateLimitAllowed(RATE_LIMIT_ACCEPT, actorId)) {
            throw new CallDomainException(CallErrorCode.RATE_LIMITED, "操作过于频繁,请稍后再试");
        }

        boolean sessionAccepting = state == CallState.ACCEPTED || state == CallState.NEGOTIATING;
        if (state == CallState.RINGING) {
            // 并发接听时，第二个请求可能在首个请求推进后才到达；守卫失败后
            // 重新加载，只要 session 已进入 ACCEPTED/NEGOTIATING 仍允许本成员加入。
            if (applySessionTransition(session, CallCommand.ACCEPT, null, null, null, null)) {
                sessionAccepting = true;
            } else {
                session = reload(session);
                state = CallState.valueOf(session.getState());
                sessionAccepting = state == CallState.ACCEPTED || state == CallState.NEGOTIATING;
            }
        }
        if (!sessionAccepting) {
            return session;
        }

        LocalDateTime now = LocalDateTime.now();
        applyParticipantTransition(session, actorId, ParticipantState.RINGING,
                ParticipantCommand.JOIN, null, now, null);
        CallParticipant joined = participantMapper.findByCallAndUser(session.getCallId(), actorId);
        if (joined == null || !ParticipantState.JOINING.name().equals(joined.getState())) {
            return session;
        }
        append(eventId, session, actorId, CallEventKind.CALL_ACCEPT,
                Map.of("mode", session.getMode(), "scope", CallScopeCodes.GROUP), traceId);
        return session;
    }

    private CallSession rejectGroupCall(CallSession session, CallParticipant participant,
                                        Long actorId, String eventId, String traceId) {
        CallState state = CallState.valueOf(session.getState());
        if (ledgerService.isReplay(eventId) || CallStateMachine.isFinal(state)) {
            return session;
        }
        if (!ParticipantState.RINGING.name().equals(participant.getState())) {
            return session;
        }
        if (isExpired(session)) {
            throw new CallDomainException(CallErrorCode.CALL_EXPIRED, "通话已过期,无法拒绝");
        }
        LocalDateTime now = LocalDateTime.now();
        applyParticipantTransition(session, actorId, ParticipantState.RINGING,
                ParticipantCommand.REJECT, CallEndReason.REJECTED.name(), null, now);
        CallParticipant rejected = participantMapper.findByCallAndUser(session.getCallId(), actorId);
        if (rejected == null || !ParticipantState.REJECTED.name().equals(rejected.getState())) {
            return session;
        }
        append(eventId, session, actorId, CallEventKind.CALL_REJECT,
                Map.of("mode", session.getMode(), "scope", CallScopeCodes.GROUP), traceId);

        // 所有被叫成员都已拒绝且 session 仍处于振铃时，收敛为 REJECTED；
        // 单个成员拒绝绝不能影响仍在等待/通话中的其他成员。
        if (state == CallState.RINGING && noPendingGroupTargets(session.getCallId())) {
            if (applySessionTransition(session, CallCommand.REJECT, CallEndReason.REJECTED.name(),
                    null, null, now)) {
                applyParticipantTransition(session, session.getInitiatorId(), ParticipantState.RINGING,
                        ParticipantCommand.CANCEL, CallEndReason.REJECTED.name(), null, now);
            }
        }
        return session;
    }

    /** 发起者取消: RINGING -> CANCELLED(终态)。被叫方调用被拒。 */
    public CallSession cancelCall(String callId, Long actorId, String eventId, String traceId) {
        assertClientEventId(eventId);
        CallSession session = requireSession(callId);
        aclService.assertAuthenticated(actorId);
        if (!session.getInitiatorId().equals(actorId)) {
            throw new CallDomainException(CallErrorCode.NOT_INITIATOR, "仅发起者可取消通话");
        }
        CallSession shortCircuit = replayOrTerminalOrOutOfOrder(session, CallCommand.CANCEL, eventId);
        if (shortCircuit != null) {
            return shortCircuit;
        }
        if (isExpired(session)) {
            throw new CallDomainException(CallErrorCode.CALL_EXPIRED, "通话已过期,等待超时回收");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!applySessionTransition(session, CallCommand.CANCEL, null, null, null, now)) {
            return reload(session);
        }
        for (CallParticipant p : participantMapper.listByCall(callId)) {
            applyParticipantTransition(session, p.getUserId(), ParticipantState.RINGING,
                    ParticipantCommand.CANCEL, null, null, now);
            applyParticipantTransition(session, p.getUserId(), ParticipantState.INVITED,
                    ParticipantCommand.CANCEL, null, null, now);
        }
        append(eventId, session, actorId, CallEventKind.CALL_CANCEL, Map.of("mode", session.getMode()), traceId);
        project(session, 0, 0);
        return session;
    }

    /**
     * 挂断(owner 或参与者):
     * NEGOTIATING -> ENDED(终态);CONNECTED -> ENDING(由 confirmEnded 收敛)。
     */
    public CallSession hangupCall(String callId, Long actorId, String eventId, String traceId) {
        assertClientEventId(eventId);
        CallSession session = requireSession(callId);
        aclService.assertAuthenticated(actorId);
        requireAnyParticipant(session, actorId);
        if (isGroup(session) && !session.getInitiatorId().equals(actorId)) {
            return leaveGroupParticipant(session, actorId, eventId, traceId, CallEventKind.CALL_HANGUP,
                    CallEndReason.HANGUP.name());
        }
        CallState state = CallState.valueOf(session.getState());
        CallSession shortCircuit = replayOrTerminalOrOutOfOrder(session, CallCommand.HANGUP, eventId);
        if (shortCircuit != null) {
            return shortCircuit;
        }
        LocalDateTime now = LocalDateTime.now();
        if (state == CallState.NEGOTIATING) {
            // 尚未接通也要保留结束原因
            if (!applySessionTransition(session, CallCommand.HANGUP, CallEndReason.HANGUP.name(), null, null, now)) {
                return reload(session);
            }
            for (CallParticipant p : participantMapper.listByCall(callId)) {
                applyParticipantTransition(session, p.getUserId(), ParticipantState.JOINING,
                        ParticipantCommand.LEAVE, CallEndReason.HANGUP.name(), null, now);
            }
            if (isGroup(session)) {
                releaseRemainingGroupParticipants(session, now, CallEndReason.HANGUP.name());
            }
            append(eventId, session, actorId, CallEventKind.CALL_HANGUP, Map.of("mode", session.getMode()), traceId);
            project(session, session.getConnectedAt() != null ? 1 : 2, ledgerService.durationSeconds(session));
            return session;
        }
        if (state == CallState.ACCEPTED) {
            if (!applySessionTransition(session, CallCommand.HANGUP, CallEndReason.HANGUP.name(), null, null, now)) {
                return reload(session);
            }
            for (CallParticipant participant : participantMapper.listByCall(callId)) {
                ParticipantState current = ParticipantState.valueOf(participant.getState());
                if (current == ParticipantState.INVITED || current == ParticipantState.RINGING) {
                    applyParticipantTransition(session, participant.getUserId(), current,
                            ParticipantCommand.CANCEL, CallEndReason.HANGUP.name(), null, now);
                } else if (ParticipantStateMachine.canTransition(current, ParticipantCommand.LEAVE)) {
                    applyParticipantTransition(session, participant.getUserId(), current,
                            ParticipantCommand.LEAVE, CallEndReason.HANGUP.name(), null, now);
                }
            }
            append(eventId, session, actorId, CallEventKind.CALL_HANGUP,
                    Map.of("mode", session.getMode(), "state", "ACCEPTED"), traceId);
            project(session, 0, 0);
            return session;
        }
        if (state == CallState.CONNECTED) {
            if (!applySessionTransition(session, CallCommand.HANGUP, CallEndReason.HANGUP.name(), null, null, now)) {
                return reload(session);
            }
            for (CallParticipant p : participantMapper.listByCall(callId)) {
                applyParticipantTransition(session, p.getUserId(), ParticipantState.CONNECTED,
                        ParticipantCommand.LEAVE, CallEndReason.HANGUP.name(), null, now);
                applyParticipantTransition(session, p.getUserId(), ParticipantState.RECONNECTING,
                        ParticipantCommand.LEAVE, CallEndReason.HANGUP.name(), null, now);
            }
            if (isGroup(session)) {
                releaseRemainingGroupParticipants(session, now, CallEndReason.HANGUP.name());
            }
            append(eventId, session, actorId, CallEventKind.CALL_HANGUP, Map.of("mode", session.getMode()), traceId);
            project(session, 1, ledgerService.durationSeconds(session));
            return session;
        }
        // 其他状态(如 RINGING)hangup 未在转移表中 → 乱序,安全返回当前状态
        return session;
    }

    /** 参与者加入(RINGING -> JOINING)。initiator 在被叫方接听后调用。 */
    public CallSession joinCall(String callId, Long actorId, String eventId, String traceId) {
        assertClientEventId(eventId);
        CallSession session = requireSession(callId);
        aclService.assertAuthenticated(actorId);
        requireAnyParticipant(session, actorId);
        CallState state = CallState.valueOf(session.getState());
        if (ledgerService.isReplay(eventId) || CallStateMachine.isFinal(state)) {
            return session;
        }
        boolean groupLateJoin = isGroup(session) && state == CallState.CONNECTED;
        if (state != CallState.ACCEPTED && state != CallState.NEGOTIATING && !groupLateJoin) {
            return session; // 乱序: 会话状态不允许加入
        }
        CallParticipant participant = participantMapper.findByCallAndUser(callId, actorId);
        if (participant == null || !ParticipantState.RINGING.name().equals(participant.getState())) {
            return session;
        }
        LocalDateTime now = LocalDateTime.now();
        applyParticipantTransition(session, actorId, ParticipantState.RINGING,
                ParticipantCommand.JOIN, null, now, null);
        append(eventId, session, actorId, CallEventKind.CALL_JOIN, Map.of("mode", session.getMode()), traceId);
        return session;
    }

    /** 参与者离开(通话中退出,不终止会话)。 */
    public CallSession leaveCall(String callId, Long actorId, String eventId, String traceId) {
        assertClientEventId(eventId);
        CallSession session = requireSession(callId);
        aclService.assertAuthenticated(actorId);
        requireAnyParticipant(session, actorId);
        if (isGroup(session)) {
            if (session.getInitiatorId().equals(actorId)) {
                // 发起者离开等价于结束整场，避免孤儿 room 和永不回收的 session。
                return hangupCall(callId, actorId, eventId, traceId);
            }
            return leaveGroupParticipant(session, actorId, eventId, traceId, CallEventKind.CALL_LEAVE, null);
        }
        CallState state = CallState.valueOf(session.getState());
        if (ledgerService.isReplay(eventId) || CallStateMachine.isFinal(state)) {
            return session;
        }
        LocalDateTime now = LocalDateTime.now();
        applyParticipantTransition(session, actorId, ParticipantState.CONNECTED,
                ParticipantCommand.LEAVE, null, null, now);
        applyParticipantTransition(session, actorId, ParticipantState.JOINING,
                ParticipantCommand.LEAVE, null, null, now);
        applyParticipantTransition(session, actorId, ParticipantState.RECONNECTING,
                ParticipantCommand.LEAVE, null, null, now);
        append(eventId, session, actorId, CallEventKind.CALL_LEAVE, Map.of("mode", session.getMode()), traceId);
        return session;
    }

    /** 群成员退出只改变本人状态；最后一个活跃参与者退出后再收敛会话。 */
    private CallSession leaveGroupParticipant(CallSession session, Long actorId, String eventId, String traceId,
                                              CallEventKind eventKind, String reason) {
        CallState state = CallState.valueOf(session.getState());
        if (ledgerService.isReplay(eventId) || CallStateMachine.isFinal(state)) {
            return session;
        }
        CallParticipant participant = participantMapper.findByCallAndUser(session.getCallId(), actorId);
        if (participant == null || isParticipantTerminal(participant.getState())) {
            return session;
        }
        LocalDateTime now = LocalDateTime.now();
        ParticipantState current = ParticipantState.valueOf(participant.getState());
        if (current == ParticipantState.RINGING || current == ParticipantState.INVITED) {
            applyParticipantTransition(session, actorId, current, ParticipantCommand.CANCEL,
                    reason != null ? reason : CallEndReason.HANGUP.name(), null, now);
        } else if (ParticipantStateMachine.canTransition(current, ParticipantCommand.LEAVE)) {
            applyParticipantTransition(session, actorId, current, ParticipantCommand.LEAVE,
                    reason, null, now);
        } else {
            return session;
        }
        CallParticipant left = participantMapper.findByCallAndUser(session.getCallId(), actorId);
        if (left == null || !isParticipantTerminal(left.getState())) {
            return session;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", session.getMode());
        payload.put("scope", CallScopeCodes.GROUP);
        payload.put("participant_state", current.name());
        append(eventId, session, actorId, eventKind, payload, traceId);
        convergeGroupIfEmpty(session, now, actorId, traceId);
        return session;
    }

    // ==================== 协商与收敛(provider 侧) ====================

    /** 协商开始: ACCEPTED -> NEGOTIATING,NEGOTIATING TTL 5 分钟开始计时 */
    public CallSession startNegotiation(String callId, Long actorId, String eventId, String traceId) {
        CallSession session = requireSession(callId);
        aclService.assertAuthenticated(actorId);
        requireAnyParticipant(session, actorId);
        CallSession shortCircuit = replayOrTerminalOrOutOfOrder(session, CallCommand.NEGOTIATE_START, eventId);
        if (shortCircuit != null) {
            return shortCircuit;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!applySessionTransition(session, CallCommand.NEGOTIATE_START, null, now.plus(negotiatingTtl), null, null)) {
            return reload(session);
        }
        append(eventId, session, actorId, CallEventKind.CALL_STATE,
                Map.of("transition", "accepted->negotiating", "expires_in", negotiatingTtl.toSeconds()), traceId);
        return session;
    }

    /** provider 确认接通: NEGOTIATING -> CONNECTED(webhook 或可信客户端) */
    public CallSession confirmConnected(String callId, Long actorId, String eventId, String traceId) {
        CallSession session = requireSession(callId);
        aclService.assertAuthenticated(actorId);
        requireAnyParticipant(session, actorId);
        CallSession shortCircuit = replayOrTerminalOrOutOfOrder(session, CallCommand.CONNECTED, eventId);
        if (shortCircuit != null) {
            return shortCircuit;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!applySessionTransition(session, CallCommand.CONNECTED, null, null, now, null)) {
            return reload(session);
        }
        for (CallParticipant p : participantMapper.listByCall(callId)) {
            applyParticipantTransition(session, p.getUserId(), ParticipantState.JOINING,
                    ParticipantCommand.CONNECTED, null, now, null);
            applyParticipantTransition(session, p.getUserId(), ParticipantState.RECONNECTING,
                    ParticipantCommand.CONNECTED, null, now, null);
        }
        append(eventId, session, actorId, CallEventKind.CALL_CONNECTED, Map.of("mode", session.getMode()), traceId);
        project(session, 1, 0);
        return session;
    }

    /** provider/webhook 收敛: ENDING -> ENDED(终态)。系统事件。 */
    public CallSession confirmEnded(String callId, String eventId, String traceId) {
        CallSession session = requireSession(callId);
        CallSession shortCircuit = replayOrTerminalOrOutOfOrder(session, CallCommand.ENDED, eventId);
        if (shortCircuit != null) {
            return shortCircuit;
        }
        LocalDateTime now = LocalDateTime.now();
        String endReason = session.getEndReason() != null ? session.getEndReason() : CallEndReason.HANGUP.name();
        if (!applySessionTransition(session, CallCommand.ENDED, endReason, null, null, now)) {
            return reload(session);
        }
        long duration = ledgerService.durationSeconds(session);
        append(eventId, session, SYSTEM_PARTICIPANT, CallEventKind.CALL_ENDED,
                Map.of("end_reason", endReason, "duration", duration), traceId);
        project(session, session.getConnectedAt() != null ? 1 : 2, duration);
        return session;
    }

    /** 系统失败: CREATED/ACCEPTED/NEGOTIATING/CONNECTED -> FAILED(终态)。 */
    public CallSession failCall(String callId, String eventId, CallEndReason reason, String traceId) {
        CallSession session = requireSession(callId);
        CallState state = CallState.valueOf(session.getState());
        CallSession shortCircuit = replayOrTerminalOrOutOfOrder(session, CallCommand.FAIL, eventId);
        if (shortCircuit != null) {
            return shortCircuit;
        }
        LocalDateTime now = LocalDateTime.now();
        String endReason = (reason != null ? reason : CallEndReason.FAILED).name();
        if (!applySessionTransition(session, CallCommand.FAIL, endReason, null, null, now)) {
            return reload(session);
        }
        for (CallParticipant p : participantMapper.listByCall(callId)) {
            ParticipantState current = ParticipantState.valueOf(p.getState());
            if (ParticipantStateMachine.canTransition(current, ParticipantCommand.FAIL)) {
                // JOINING/RECONNECTING -> FAILED
                participantMapper.transitionParticipant(callId, p.getUserId(), current.name(),
                        ParticipantState.FAILED.name(), endReason, null, now);
            } else if (ParticipantStateMachine.canTransition(current, ParticipantCommand.LEAVE)) {
                // CONNECTED -> LEFT(§3 参与者表无 CONNECTED+FAIL,按 LEFT 退出)
                participantMapper.transitionParticipant(callId, p.getUserId(), current.name(),
                        ParticipantState.LEFT.name(), endReason, null, now);
            } else if (ParticipantStateMachine.canTransition(current, ParticipantCommand.CANCEL)) {
                // 仍在 RINGING/INVITED 的参与者: 通话失败等同于被取消释放
                participantMapper.transitionParticipant(callId, p.getUserId(), current.name(),
                        ParticipantState.CANCELLED.name(), endReason, null, now);
            }
        }
        append(eventId, session, SYSTEM_PARTICIPANT, CallEventKind.CALL_FAILED,
                Map.of("end_reason", endReason), traceId);
        project(session, session.getConnectedAt() != null ? 1 : 0, ledgerService.durationSeconds(session));
        return session;
    }

    /**
     * 超时处理(TTL worker 调用,worker 安全: 会话不存在或已终态返回当前)。
     * RINGING -> EXPIRED;NEGOTIATING -> FAILED(end_reason=expired)。
     */
    public CallSession expireCall(String callId, String eventId, String traceId) {
        CallSession session = sessionMapper.findByCallId(callId);
        if (session == null || CallStateMachine.isFinal(CallState.valueOf(session.getState()))) {
            return session;
        }
        CallState state = CallState.valueOf(session.getState());
        if (state == CallState.RINGING) {
            if (!isExpired(session)) {
                return session;
            }
            LocalDateTime now = LocalDateTime.now();
            if (!applySessionTransition(session, CallCommand.EXPIRE, CallEndReason.EXPIRED.name(), null, null, now)) {
                return reload(session);
            }
            for (CallParticipant p : participantMapper.listByCall(callId)) {
                applyParticipantTransition(session, p.getUserId(), ParticipantState.RINGING,
                        ParticipantCommand.CANCEL, CallEndReason.EXPIRED.name(), null, now);
                applyParticipantTransition(session, p.getUserId(), ParticipantState.INVITED,
                        ParticipantCommand.CANCEL, CallEndReason.EXPIRED.name(), null, now);
            }
            append(eventId, session, SYSTEM_PARTICIPANT, CallEventKind.CALL_EXPIRED,
                    Map.of("end_reason", CallEndReason.EXPIRED.name(), "source", "ttl-worker"), traceId);
            project(session, 2, 0);
            return session;
        }
        if (state == CallState.NEGOTIATING) {
            if (!isExpired(session)) {
                return session;
            }
            LocalDateTime now = LocalDateTime.now();
            if (!applySessionTransition(session, CallCommand.FAIL, CallEndReason.EXPIRED.name(), null, null, now)) {
                return reload(session);
            }
            for (CallParticipant p : participantMapper.listByCall(callId)) {
                applyParticipantTransition(session, p.getUserId(), ParticipantState.JOINING,
                        ParticipantCommand.FAIL, CallEndReason.EXPIRED.name(), null, now);
            }
            append(eventId, session, SYSTEM_PARTICIPANT, CallEventKind.CALL_FAILED,
                    Map.of("end_reason", CallEndReason.EXPIRED.name(), "source", "ttl-worker"), traceId);
            return session;
        }
        return session;
    }

    // ==================== 查询 ====================

    /** 重复查询(终态语义之一)。会话不存在返回 null。 */
    public CallSession getCall(String callId) {
        return sessionMapper.findByCallId(callId);
    }

    public CallParticipant getParticipant(String callId, Long userId) {
        return participantMapper.findByCallAndUser(callId, userId);
    }

    public List<CallParticipant> participants(String callId) {
        return participantMapper.listByCall(callId);
    }

    public List<CallEvent> events(String callId) {
        return ledgerService.eventsByCall(callId);
    }

    public List<CallEvent> events(String callId, Long participantId) {
        return ledgerService.eventsByCallAndParticipant(callId, participantId);
    }

    public List<CompatCallProjection> callHistory(String callId) {
        return ledgerService.projectionsByCall(callId);
    }

    // ==================== 内部工具 ====================

    /** 客户端 event_id 前缀校验: sys:/ttl: 归服务端(worker/token/webhook),禁止客户端预占 */
    private void assertClientEventId(String eventId) {
        if (eventId != null
                && (eventId.startsWith(SYSTEM_EVENT_PREFIX) || eventId.startsWith(TTL_EVENT_PREFIX))) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT,
                    "event_id 保留前缀 sys:/ttl:,禁止客户端使用");
        }
    }

    private void assertAvailableForCall(Long userId) {
        if (userId == null) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "缺少通话参与者");
        }
        CallSession active = sessionMapper.findActiveByUserId(userId);
        if (active != null) {
            throw new CallDomainException(CallErrorCode.BUSY,
                    "用户正在通话中,请稍后再试");
        }
    }

    private CallSession requireSession(String callId) {
        CallSession session = sessionMapper.findByCallId(callId);
        if (session == null) {
            throw new CallDomainException(CallErrorCode.SESSION_NOT_FOUND, "通话不存在: " + callId);
        }
        return session;
    }

    /** 被叫方校验: 必须是通话参与者且不是发起者 */
    private CallParticipant requireTargetParticipant(CallSession session, Long actorId) {
        CallParticipant participant = participantMapper.findByCallAndUser(session.getCallId(), actorId);
        if (participant == null) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED, "不是通话参与者");
        }
        if ("initiator".equals(participant.getRole())) {
            throw new CallDomainException(CallErrorCode.NOT_TARGET, "发起者不能接受/拒绝自己的呼叫");
        }
        return participant;
    }

    /** 任意参与者校验 */
    private void requireAnyParticipant(CallSession session, Long actorId) {
        if (participantMapper.findByCallAndUser(session.getCallId(), actorId) == null) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED, "不是通话参与者");
        }
    }

    /**
     * 重放/终态/乱序短路。返回 null 表示可以继续执行命令;
     * 否则返回应返回的当前状态(不创建新记录)。
     */
    private CallSession replayOrTerminalOrOutOfOrder(CallSession session, CallCommand command, String eventId) {
        CallState state = CallState.valueOf(session.getState());
        if (ledgerService.isReplay(eventId)) {
            return session; // 相同 event_id 重放
        }
        if (CallStateMachine.isFinal(state)) {
            return session; // 终态后新命令
        }
        if (!CallStateMachine.canTransition(state, command)) {
            return session; // 乱序命令,安全返回当前状态
        }
        return null;
    }

    /**
     * 执行会话状态迁移(守卫式)。成功返回 true;
     * 并发下守卫失败返回 false(调用方应重新加载)。
     */
    private boolean applySessionTransition(CallSession session, CallCommand command,
                                           String endReason, LocalDateTime expiresAt,
                                           LocalDateTime connectedAt, LocalDateTime endedAt) {
        CallState target = CallStateMachine.transition(CallState.valueOf(session.getState()), command);
        int rows = sessionMapper.transitionSession(session.getCallId(), session.getState(), target.name(),
                endReason, endedAt, connectedAt, expiresAt);
        if (rows == 0) {
            log.warn("[CALL] 守卫更新失败(并发/乱序): callId={} expected={} target={}", session.getCallId(), session.getState(), target);
            return false;
        }
        session.setState(target.name());
        session.setEndReason(endReason);
        session.setEndedAt(endedAt);
        if (connectedAt != null) {
            session.setConnectedAt(connectedAt);
        }
        session.setExpiresAt(expiresAt);
        return true;
    }

    /** 参与者状态迁移(守卫式,0 行说明不在 expected 状态,跳过) */
    private void applyParticipantTransition(CallSession session, Long userId, ParticipantState expected,
                                            ParticipantCommand command, String reason,
                                            LocalDateTime joinedAt, LocalDateTime leftAt) {
        ParticipantState target = ParticipantStateMachine.transition(expected, command);
        participantMapper.transitionParticipant(session.getCallId(), userId, expected.name(), target.name(),
                reason, joinedAt, leftAt);
    }

    private void insertParticipant(String callId, Long userId, String role, GroupMemberProfile profile) {
        CallParticipant participant = new CallParticipant();
        participant.setCallId(callId);
        participant.setUserId(userId);
        participant.setRole(role);
        participant.setState(ParticipantState.INVITED.name());
        participant.setProfileSnapshot(profileSnapshot(userId, profile));
        participantMapper.insert(participant);
    }

    private String profileSnapshot(Long userId, GroupMemberProfile profile) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("user_id", userId);
        if (profile != null) {
            if (profile.getNickname() != null && !profile.getNickname().isBlank()) {
                snapshot.put("nickname", profile.getNickname());
            }
            if (profile.getAvatar() != null && !profile.getAvatar().isBlank()) {
                snapshot.put("avatar", profile.getAvatar());
            }
        }
        try {
            return SNAPSHOT_MAPPER.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            // ObjectMapper 序列化简单标量不应失败；若运行时配置异常也不能
            // 让已建立的通话缺少 roster 快照，保留最小结构作为确定性兜底。
            return "{\"user_id\":" + userId + "}";
        }
    }

    /** 所有参与者 INVITED -> RINGING */
    private void ringParticipants(CallSession session, Long initiator, List<Long> targets) {
        List<Long> users = new ArrayList<>();
        users.add(initiator);
        users.addAll(targets);
        for (Long userId : users) {
            applyParticipantTransition(session, userId, ParticipantState.INVITED,
                    ParticipantCommand.RING, null, null, null);
        }
    }

    private boolean isGroup(CallSession session) {
        return session != null && CallScopeCodes.GROUP.equals(session.getScope());
    }

    private boolean isParticipantTerminal(String state) {
        if (state == null) {
            return true;
        }
        try {
            return ParticipantStateMachine.isFinal(ParticipantState.valueOf(state));
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /** 被叫成员是否都已拒绝/取消/离开(发起者不计入待接听目标)。 */
    private boolean noPendingGroupTargets(String callId) {
        for (CallParticipant participant : participantMapper.listByCall(callId)) {
            if (!"initiator".equals(participant.getRole())
                    && isActiveParticipantState(participant.getState())) {
                return false;
            }
        }
        return true;
    }

    private boolean hasActiveGroupParticipants(String callId) {
        for (CallParticipant participant : participantMapper.listByCall(callId)) {
            if (ParticipantState.JOINING.name().equals(participant.getState())
                    || ParticipantState.CONNECTED.name().equals(participant.getState())
                    || ParticipantState.RECONNECTING.name().equals(participant.getState())) {
                return true;
            }
        }
        return false;
    }

    private boolean isActiveParticipantState(String state) {
        return ParticipantState.INVITED.name().equals(state)
                || ParticipantState.RINGING.name().equals(state)
                || ParticipantState.JOINING.name().equals(state)
                || ParticipantState.CONNECTED.name().equals(state)
                || ParticipantState.RECONNECTING.name().equals(state);
    }

    /** 发起者结束 group session 时释放尚未接听的成员，避免终态下残留 RINGING roster。 */
    private void releaseRemainingGroupParticipants(CallSession session, LocalDateTime now, String reason) {
        for (CallParticipant participant : participantMapper.listByCall(session.getCallId())) {
            Long userId = participant.getUserId();
            if (ParticipantState.RINGING.name().equals(participant.getState())) {
                applyParticipantTransition(session, userId, ParticipantState.RINGING,
                        ParticipantCommand.CANCEL, reason, null, now);
            } else if (ParticipantState.INVITED.name().equals(participant.getState())) {
                applyParticipantTransition(session, userId, ParticipantState.INVITED,
                        ParticipantCommand.CANCEL, reason, null, now);
            }
        }
    }

    /** 无活跃成员时关闭 group session；CONNECTED 留给 room_finished 收敛。 */
    private void convergeGroupIfEmpty(CallSession session, LocalDateTime now, Long actorId, String traceId) {
        if (!isGroup(session)) {
            return;
        }
        CallState state = CallState.valueOf(session.getState());
        if (state == CallState.RINGING && noPendingGroupTargets(session.getCallId())) {
            if (applySessionTransition(session, CallCommand.REJECT, CallEndReason.REJECTED.name(),
                    null, null, now)) {
                applyParticipantTransition(session, session.getInitiatorId(), ParticipantState.RINGING,
                        ParticipantCommand.CANCEL, CallEndReason.REJECTED.name(), null, now);
                append(systemEventId(), session, SYSTEM_PARTICIPANT, CallEventKind.CALL_STATE,
                        Map.of("transition", "group-empty->rejected", "actor_id", actorId), traceId);
            }
            return;
        }
        if (hasActiveGroupParticipants(session.getCallId())) {
            return;
        }
        if (state == CallState.NEGOTIATING) {
            if (applySessionTransition(session, CallCommand.HANGUP, CallEndReason.HANGUP.name(),
                    null, null, now)) {
                append(systemEventId(), session, SYSTEM_PARTICIPANT, CallEventKind.CALL_STATE,
                        Map.of("transition", "group-empty->ended", "actor_id", actorId), traceId);
            }
        } else if (state == CallState.CONNECTED) {
            if (applySessionTransition(session, CallCommand.HANGUP, CallEndReason.HANGUP.name(),
                    null, null, now)) {
                append(systemEventId(), session, SYSTEM_PARTICIPANT, CallEventKind.CALL_STATE,
                        Map.of("transition", "group-empty->ending", "actor_id", actorId), traceId);
            }
        }
    }

    private boolean isExpired(CallSession session) {
        return session.getExpiresAt() != null && !session.getExpiresAt().isAfter(LocalDateTime.now());
    }

    private CallSession reload(CallSession session) {
        CallSession fresh = sessionMapper.findByCallId(session.getCallId());
        return fresh != null ? fresh : session;
    }

    private boolean rateLimitAllowed(String action, Long userId) {
        int max = RATE_LIMIT_ACCEPT.equals(action) ? RATE_LIMIT_ACCEPT_MAX : RATE_LIMIT_CREATE_MAX;
        return redisCacheService.rateLimit(action, String.valueOf(userId), max, RATE_LIMIT_WINDOW);
    }

    private void append(String eventId, CallSession session, Long participantId, CallEventKind kind,
                        Map<String, ?> payload, String traceId) {
        String effective = eventId != null && !eventId.isBlank() ? eventId : systemEventId();
        ledgerService.append(effective, session.getCallId(), participantId, kind, payload, traceId);
    }

    private String systemEventId() {
        return SYSTEM_EVENT_PREFIX + UUID.randomUUID();
    }

    /** 兼容投影落库(仅 direct,方向恒为 发起者 -> 被叫方),失败不阻断主流程 */
    private void project(CallSession session, int callState, long duration) {
        Long callee = calleeOf(session);
        if (callee == null) {
            return;
        }
        ledgerService.projectCallMessage(session, callee, callState, duration);
    }

    /** direct 通话中被叫方;group 通话返回 null(投影仅 direct) */
    private Long calleeOf(CallSession session) {
        if (!CallScopeCodes.DIRECT.equals(session.getScope())) {
            return null;
        }
        for (CallParticipant p : participantMapper.listByCall(session.getCallId())) {
            if (!"initiator".equals(p.getRole())) {
                return p.getUserId();
            }
        }
        return null;
    }

    /** 常量隔离,避免与 CallScope 枚举(含 live-interactive)耦合 */
    private static final class CallScopeCodes {
        private static final String DIRECT = "direct";
        private static final String GROUP = "group";
    }
}
