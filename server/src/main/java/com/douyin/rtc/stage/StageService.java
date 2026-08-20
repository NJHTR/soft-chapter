package com.douyin.rtc.stage;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Stage 状态机服务(契约 §6)。
 *
 * <p>不变式:
 * <ul>
 *   <li>event_id 重放返回第一次结果,不重复记审计、不推进状态;</li>
 *   <li>generation 单调;携带旧 generation 的命令抛 {@link CallErrorCode#GENERATION_STALE},不会复活成员;</li>
 *   <li>REQUEST 仅本人;APPROVE/DEMOTE/REVOKE 仅主持人(本人可 DEMOTE 自己);</li>
 *   <li>APPROVE 受 cap-publishers 硬上限约束(STAGE_LIMIT_REACHED);</li>
 *   <li>DEMOTING/REVOKING 必须先经 {@link StageProviderPort#revokePublishPermission} 移除
 *       发布权限,失败则置 providerPending 并由 reconciler 重试/断开确认,不能提前标记已撤;</li>
 *   <li>审计条目 userId 脱敏(SHA-256 前 16 hex)。</li>
 * </ul>
 */
@Slf4j
@Service
public class StageService {

    /** provider/webhook 注入内部确认事件时的系统 actor,免主持人检查。 */
    public static final long SYSTEM_ACTOR = -1L;

    private final StageStore store;
    private final StageProviderPort providerPort;
    private final StageProperties properties;

    public StageService(StageStore store, StageProviderPort providerPort, StageProperties properties) {
        this.store = store;
        this.providerPort = providerPort;
        this.properties = properties;
    }

    /**
     * 应用一个命令事件。返回应用后的成员;重放时返回已记录结果。
     */
    public StageMember apply(StageEvent event) {
        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "缺少 event_id");
        }
        StageMember member = store.find(event.liveId(), event.userId());
        if (event.eventId().equals(member.lastEventId())) {
            log.debug("[STAGE] replay ignored: {} {}", event.eventId(), member);
            return member;
        }
        if (event.generation() != member.generation()) {
            throw new CallDomainException(CallErrorCode.GENERATION_STALE,
                    "Stage 命令携带过期 generation " + event.generation() + ",当前 " + member.generation());
        }
        authorize(event, member);

        StageMemberStatus target = StageStateMachine.transition(member.status(), event.command());
        StageMember next = member.with(target, member.generation() + 1, event.eventId(),
                member.providerPending(), event.requestedAtEpochMs());

        if (target == StageMemberStatus.DEMOTING || target == StageMemberStatus.REVOKING) {
            next = settleProvider(next, event);
        }

        if (!store.compareAndPut(member, next)) {
            // 并发下仓库已被推进:以新值重放判重(不抛出、不复活旧状态)
            StageMember current = store.find(event.liveId(), event.userId());
            if (event.eventId().equals(current.lastEventId())) {
                return current;
            }
            throw new CallDomainException(CallErrorCode.GENERATION_STALE,
                    "Stage 并发冲突,请携带最新 generation 重试");
        }
        appendAudit(event, member.status(), next.status());
        log.info("[STAGE] {} live={} user={} actor={} {} -> {} gen={} event={}",
                event.command(), event.liveId(), event.userId(), event.actorUserId(),
                member.status(), next.status(), next.generation(), event.eventId());
        return next;
    }

    /**
     * provider 确认 REVOKING 发布权限已移除 -> REVOKED(内部命令,来自 webhook/查询)。
     */
    public StageMember confirmRevoked(long liveId, long userId, String eventId, long generation) {
        StageMember member = store.find(liveId, userId);
        if (member.status() != StageMemberStatus.REVOKING) {
            throw new CallDomainException(CallErrorCode.INVALID_STATE_TRANSITION,
                    "仅 REVOKING 可确认撤销,当前 " + member.status());
        }
        if (eventId == null || eventId.isBlank()) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "缺少 event_id");
        }
        return apply(new StageEvent(StageCommand.CONFIRM_REVOKED, liveId, userId, SYSTEM_ACTOR,
                eventId, generation, System.currentTimeMillis()));
    }

    /**
     * provider 确认 DEMOTING 已断开/离开 -> AUDIENCE(内部命令,来自 webhook/查询)。
     */
    public StageMember confirmLeft(long liveId, long userId, String eventId, long generation) {
        StageMember member = store.find(liveId, userId);
        if (member.status() != StageMemberStatus.DEMOTING) {
            throw new CallDomainException(CallErrorCode.INVALID_STATE_TRANSITION,
                    "仅 DEMOTING 可确认离开,当前 " + member.status());
        }
        if (eventId == null || eventId.isBlank()) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "缺少 event_id");
        }
        return apply(new StageEvent(StageCommand.CONFIRM_LEFT, liveId, userId, SYSTEM_ACTOR,
                eventId, generation, System.currentTimeMillis()));
    }

    /**
     * Stage -&gt; SRS 出流(经 provider 控制端口,媒体不经过 Spring/Kafka/聊天 WS)。
     * 仅在 egress 启用且有在台发布者时允许。
     */
    public String requestStageEgress(long liveId, String roomName, long actorUserId, String eventId) {
        if (!properties.getEgress().isEnabled()) {
            throw new CallDomainException(CallErrorCode.PROVIDER_ERROR, "Egress 未启用(rtc.stage.egress.enabled=false)");
        }
        List<StageMember> onStage = store.members(liveId).stream()
                .filter(m -> m.status() == StageMemberStatus.ON_STAGE)
                .toList();
        if (onStage.isEmpty()) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "Stage 没有在台发布者,不能启动 Egress");
        }
        String ref = providerPort.requestStageEgress(String.valueOf(liveId), roomName);
        store.appendAudit(new StageAuditEntry(liveId, StageAuditEntry.redact(onStage.get(0).userId()),
                StageAuditEntry.redact(actorUserId), StageCommand.EGRESS_START, eventId,
                onStage.get(0).generation(), StageMemberStatus.ON_STAGE, StageMemberStatus.ON_STAGE,
                System.currentTimeMillis()));
        log.info("[STAGE-EGRESS] live={} room={} ref={}", liveId, roomName, ref);
        return ref;
    }

    /** 需要 provider 收敛(DEMOTING/REVOKING 且权限移除未确认)的成员,供 generation-aware reconciler 重试。 */
    public List<StageMember> pendingProviderSettlement(long liveId) {
        return store.members(liveId).stream()
                .filter(m -> m.status().providerSettlementPending() && m.providerPending())
                .toList();
    }

    public List<StageMember> members(long liveId) {
        return store.members(liveId);
    }

    public List<StageAuditEntry> audit(long liveId, int limit) {
        return store.audit(liveId, limit <= 0 ? 100 : limit);
    }

    // ==================== 内部 ====================

    private StageMember settleProvider(StageMember pending, StageEvent event) {
        boolean revoked = providerPort.revokePublishPermission(pending);
        if (revoked) {
            return pending.with(pending.status(), pending.generation(), pending.lastEventId(),
                    false, event.requestedAtEpochMs());
        }
        log.warn("[STAGE] provider 发布权限移除未确认: {} 事件 {} 保持 pending,等待 reconciler",
                pending, event.eventId());
        return pending.with(pending.status(), pending.generation(), pending.lastEventId(),
                true, event.requestedAtEpochMs());
    }

    private void authorize(StageEvent event, StageMember member) {
        switch (event.command()) {
            case REQUEST -> {
                if (event.actorUserId() != event.userId()) {
                    throw new CallDomainException(CallErrorCode.NOT_TARGET,
                            "stage.request 仅本人可发起");
                }
            }
            case APPROVE, REVOKE, JOINED, LEFT -> {
                requireHost(event.actorUserId());
            }
            case CONFIRM_LEFT, CONFIRM_REVOKED -> {
                if (event.actorUserId() != SYSTEM_ACTOR) {
                    requireHost(event.actorUserId());
                }
            }
            case DEMOTE -> {
                if (event.actorUserId() != event.userId()) {
                    requireHost(event.actorUserId());
                }
            }
        }
        if (event.command() == StageCommand.APPROVE && !member.status().holdsPublishSlot()
                && publisherCount(event.liveId()) >= properties.getCapPublishers()) {
            throw new CallDomainException(CallErrorCode.STAGE_LIMIT_REACHED,
                    "Stage 发布者已达硬上限 " + properties.getCapPublishers());
        }
    }

    private void requireHost(long actorUserId) {
        if (!properties.getHostUserIds().contains(actorUserId)) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED,
                    "仅主持人可执行此命令");
        }
    }

    private int publisherCount(long liveId) {
        return (int) store.members(liveId).stream()
                .filter(m -> m.status().holdsPublishSlot())
                .count();
    }

    private void appendAudit(StageEvent event, StageMemberStatus from, StageMemberStatus to) {
        store.appendAudit(new StageAuditEntry(event.liveId(),
                StageAuditEntry.redact(event.userId()),
                StageAuditEntry.redact(event.actorUserId()),
                event.command(), event.eventId(), event.generation(), from, to,
                event.requestedAtEpochMs()));
    }
}