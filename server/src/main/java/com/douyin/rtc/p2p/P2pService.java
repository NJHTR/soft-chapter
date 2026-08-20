package com.douyin.rtc.p2p;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RTC-015 受控 1 对 1 P2P 控制面(契约第 7 节)。
 *
 * 只做状态/consent/generation/审计编排;不下发 STUN/TURN 决策外的任何媒体动作,
 * 不持久化 SDP/ICE 原文。真实双浏览器/NAT 验收前功能开关必须保持关闭(fail-closed)。
 */
public class P2pService {

    public static final long SYSTEM_ACTOR = -1L;

    private final P2pStateStore store;
    private final P2pProperties properties;
    private final P2pMetrics metrics;
    private final P2pSignalValidator validator;
    private final P2pMailbox mailbox;
    private final Clock clock;
    private final String effectiveSecret;
    private final List<P2pAuditEntry> auditTrail = new ArrayList<>();

    public P2pService(P2pStateStore store, P2pProperties properties, P2pMetrics metrics) {
        this(store, properties, metrics, Clock.systemUTC());
    }

    public P2pService(P2pStateStore store, P2pProperties properties, P2pMetrics metrics, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
        this.effectiveSecret = (properties.getSignalingSecret() == null || properties.getSignalingSecret().isBlank())
                ? UUID.randomUUID().toString() : properties.getSignalingSecret();
        this.validator = new P2pSignalValidator(effectiveSecret, properties.getSignalingMaxBytes(),
                properties.getMaxIceCandidates(), properties.getMailboxTtlSeconds() * 1000L,
                properties.getSignalRatePerSecond());
        this.mailbox = new P2pMailbox(properties.getMailboxMaxEntries(), properties.getMailboxTtlSeconds() * 1000L);
    }

    private long now() {
        return clock.millis();
    }

    // ===== eligibility =====

    /** 以 CallService 快照 context 评估;仅全部满足才 ELIGIBLE,否则保持 DISABLED。 */
    public P2pStatusView evaluate(String callId, long topologyGeneration, Map<String, String> context, String eventId, Long actorUserId) {
        if (!properties.isEnabled()) {
            throw new CallDomainException(CallErrorCode.P2P_NOT_ELIGIBLE, "RTC_P2P_ENABLED=false (fail-closed)");
        }
        Map<String, String> enriched = new java.util.HashMap<>(context);
        enriched.put(P2pPolicyRule.KEY_FEATURE_ENABLED, String.valueOf(properties.isEnabled()));
        P2pEligibility eligibility = P2pPolicyRule.evaluate(enriched);
        String current = store.getStatus(callId);
        if (eligibility.eligible()) {
            if (current == null) {
                store.casStatus(callId, null, P2pTopologyStatus.ELIGIBLE.name(), null);
            } else if (P2pTopologyStatus.DISABLED.name().equals(current)) {
                store.casStatus(callId, current, P2pTopologyStatus.ELIGIBLE.name(), null);
            }
        } else {
            if (current == null) {
                store.casStatus(callId, null, P2pTopologyStatus.DISABLED.name(), null);
            }
        }
        appendAudit(callId, topologyGeneration, actorUserId, "evaluate", eligibility.eligible() ? "eligible" : eligibility.failedReasons().toString(), eventId);
        return view(callId, topologyGeneration);
    }

    // ===== consent =====

    /** 双方各自同意;双方都同意且未过期 -> CONSENTED 并记录 consent pair 指标。 */
    public P2pStatusView consent(String callId, long topologyGeneration, Long actorUserId, String eventId) {
        requireEligibleOrConsented(callId);
        P2pConsentState consent = store.upsertConsent(callId, topologyGeneration, String.valueOf(actorUserId),
                eventId, now() + properties.getConsentTtlSeconds() * 1000L);
        if (consent.bothGranted()) {
            boolean ok = store.casStatus(callId, P2pTopologyStatus.ELIGIBLE.name(),
                    P2pTopologyStatus.CONSENTED.name(), null);
            if (!ok && !P2pTopologyStatus.CONSENTED.name().equals(store.getStatus(callId))) {
                throw new CallDomainException(CallErrorCode.GENERATION_STALE, "consent 双方到达但状态机拒绝进入 CONSENTED");
            }
            metrics.recordConsentPair();
        }
        appendAudit(callId, topologyGeneration, actorUserId, "consent", "granted", eventId);
        return view(callId, topologyGeneration);
    }

    /** 撤销本人 consent:CONSENTED -> ELIGIBLE;PROBING 中 -> FALLING_BACK(PERMISSION_REVOKED)。 */
    public P2pStatusView revokeConsent(String callId, long topologyGeneration, Long actorUserId, String eventId) {
        String status = requireExisting(callId);
        if (P2pTopologyStatus.PROBING.name().equals(status)) {
            store.casStatus(callId, status, P2pTopologyStatus.FALLING_BACK.name(), P2pFallbackReason.PERMISSION_REVOKED.name());
            metrics.recordFallback(P2pFallbackReason.PERMISSION_REVOKED.name());
        } else {
            store.revokeConsent(callId, topologyGeneration, String.valueOf(actorUserId));
            if (P2pTopologyStatus.CONSENTED.name().equals(status)) {
                boolean ok = store.casStatus(callId, status, P2pTopologyStatus.ELIGIBLE.name(), null);
                if (!ok) {
                    throw new CallDomainException(CallErrorCode.GENERATION_STALE, "撤销 consent 时状态已被并发修改");
                }
            }
        }
        appendAudit(callId, topologyGeneration, actorUserId, "consent", "revoked", eventId);
        return view(callId, topologyGeneration);
    }

    // ===== probe =====

    /** 开始有界探测:COSENTED 且双方 consent 未过期;过期则直接回退 CONSENT_EXPIRED。 */
    public P2pStatusView probeStart(String callId, long topologyGeneration, Long actorUserId, String eventId) {
        String status = requireExisting(callId);
        if (!P2pTopologyStatus.CONSENTED.name().equals(status)) {
            throw new CallDomainException(CallErrorCode.INVALID_STATE_TRANSITION, "probe 仅可在 CONSENTED 开始,当前 " + status);
        }
        P2pConsentState consent = store.getConsent(callId);
        if (consent == null || !consent.bothGrantedAt(now())) {
            store.casStatus(callId, P2pTopologyStatus.CONSENTED.name(), P2pTopologyStatus.FALLING_BACK.name(),
                    P2pFallbackReason.CONSENT_EXPIRED.name());
            metrics.recordFallback(P2pFallbackReason.CONSENT_EXPIRED.name());
            appendAudit(callId, topologyGeneration, actorUserId, "probe", "consent expired -> fallback", eventId);
            return view(callId, topologyGeneration);
        }
        long deadline = now() + properties.getProbeBudgetMs();
        if (!store.casStatus(callId, status, P2pTopologyStatus.PROBING.name(), null)) {
            throw new CallDomainException(CallErrorCode.GENERATION_STALE, "probe 启动失败:状态并发变化");
        }
        probeDeadlines.put(callId, deadline);
        appendAudit(callId, topologyGeneration, actorUserId, "probe", "started budget=" + properties.getProbeBudgetMs() + "ms", eventId);
        return view(callId, topologyGeneration);
    }

    private final Map<String, Long> probeDeadlines = new java.util.concurrent.ConcurrentHashMap<>();

    /** 探测结果:selected pair 均非 relay -> P2P_CONNECTED(direct/srflx);relay -> FALLING_BACK。 */
    public P2pStatusView probeResult(String callId, long topologyGeneration, P2pCandidateClassification.ProbeOutcome outcome,
                                     String localCandidateType, String remoteCandidateType, Long rttMs, Long lossPct,
                                     String eventId, Long actorUserId) {
        String status = requireExisting(callId);
        if (!P2pTopologyStatus.PROBING.name().equals(status)) {
            throw new CallDomainException(CallErrorCode.INVALID_STATE_TRANSITION, "probe result 仅可在 PROBING,当前 " + status);
        }
        probeDeadlines.remove(callId);
        if (outcome == P2pCandidateClassification.ProbeOutcome.DIRECT
                || outcome == P2pCandidateClassification.ProbeOutcome.SRFLX) {
            store.casStatus(callId, status, P2pTopologyStatus.P2P_CONNECTED.name(), null);
            metrics.recordAttempt(outcome.name().toLowerCase());
            appendAudit(callId, topologyGeneration, actorUserId, "probe", "passed " + outcome + " local=" + localCandidateType
                    + " remote=" + remoteCandidateType + " rtt=" + rttMs + "ms loss=" + lossPct + "%", eventId);
        } else {
            P2pFallbackReason reason = P2pCandidateClassification.fallbackReasonFor(outcome);
            store.casStatus(callId, status, P2pTopologyStatus.FALLING_BACK.name(), reason.name());
            metrics.recordFallback(reason.name());
            appendAudit(callId, topologyGeneration, actorUserId, "probe", "failed " + reason + " local=" + localCandidateType
                    + " remote=" + remoteCandidateType, eventId);
        }
        return view(callId, topologyGeneration);
    }

    /** 探测预算到期或显式回退(权限/功能/质量)。任意受控态 -> FALLING_BACK + 稳定原因。 */
    public P2pStatusView fallback(String callId, long topologyGeneration, P2pFallbackReason reason, String eventId, Long actorUserId) {
        String status = requireExisting(callId);
        store.casStatus(callId, status, P2pTopologyStatus.FALLING_BACK.name(), reason.name());
        probeDeadlines.remove(callId);
        metrics.recordFallback(reason.name());
        appendAudit(callId, topologyGeneration, actorUserId, "fallback", "reason=" + reason.name(), eventId);
        return view(callId, topologyGeneration);
    }

    /** 控制面确认 SFU 已连接:FALLING_BACK -> SFU_CONNECTED。 */
    public P2pStatusView confirmSfu(String callId, long topologyGeneration, String eventId) {
        String status = requireExisting(callId);
        if (!P2pTopologyStatus.FALLING_BACK.name().equals(status)) {
            throw new CallDomainException(CallErrorCode.INVALID_STATE_TRANSITION, "SFU 确认仅可在 FALLING_BACK,当前 " + status);
        }
        store.casStatus(callId, status, P2pTopologyStatus.SFU_CONNECTED.name(), null);
        appendAudit(callId, topologyGeneration, SYSTEM_ACTOR, "sfu", "connected", eventId);
        return view(callId, topologyGeneration);
    }

    /** 控制面失败收敛:FALLING_BACK -> FAILED。 */
    public P2pStatusView fail(String callId, long topologyGeneration, String reason, String eventId) {
        String status = requireExisting(callId);
        if (!P2pTopologyStatus.FALLING_BACK.name().equals(status)) {
            throw new CallDomainException(CallErrorCode.INVALID_STATE_TRANSITION, "FAIL 仅可在 FALLING_BACK,当前 " + status);
        }
        store.casStatus(callId, status, P2pTopologyStatus.FAILED.name(), null);
        appendAudit(callId, topologyGeneration, SYSTEM_ACTOR, "fail", String.valueOf(reason), eventId);
        return view(callId, topologyGeneration);
    }

    // ===== signaling =====

    /**
     * 认证信令中继:校验成员/consent/generation/签名/大小/候选数/seq/速率后投递邮箱。
     * 返回投递 seq;幂等重放返回首次 seq 不重复投递。
     */
    public long relaySignal(String callId, long topologyGeneration, P2pSignalingEnvelope envelope, Long actorUserId,
                            List<Long> participants) {
        validator.validateStructure(envelope, now());
        validator.verifySignature(envelope);
        if (!envelope.getCallId().equals(callId) || envelope.getTopologyGeneration() != topologyGeneration) {
            throw new CallDomainException(CallErrorCode.GENERATION_STALE, "call/generation 不匹配");
        }
        if (!envelope.getFromUserId().equals(actorUserId)) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED, "envelope from 必须为本人");
        }
        if (!participants.contains(envelope.getFromUserId()) || !participants.contains(envelope.getToUserId())) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED, "信令双方必须为通话成员");
        }
        P2pConsentState consent = store.getConsent(callId);
        if (consent == null || !consent.bothGrantedAt(now())) {
            throw new CallDomainException(CallErrorCode.P2P_CONSENT_REQUIRED, "信令前需要双方有效 consent");
        }
        String status = store.getStatus(callId);
        if (!(P2pTopologyStatus.CONSENTED.name().equals(status) || P2pTopologyStatus.PROBING.name().equals(status))) {
            throw new CallDomainException(CallErrorCode.INVALID_STATE_TRANSITION, "信令仅可在 CONSENTED/PROBING,当前 " + status);
        }
        long seq = mailbox.put(envelope, now(), properties.getSignalRatePerSecond());
        appendAudit(callId, topologyGeneration, actorUserId, "signal:" + envelope.getKind(),
                "seq=" + seq + " digest=" + P2pDigest.envelopeDigest(envelope), envelope.getEventId());
        return seq;
    }

    /** 取走本人收件箱(瞬态清空,不落库)。 */
    public List<P2pSignalingEnvelope> takeInbox(String callId, Long actorUserId) {
        return mailbox.take(callId, actorUserId, now());
    }

    // ===== read / helpers =====

    public P2pStatusView status(String callId, long topologyGeneration) {
        return view(callId, topologyGeneration);
    }

    public List<P2pAuditEntry> audit(String callId) {
        List<P2pAuditEntry> result = new ArrayList<>();
        for (P2pAuditEntry e : auditTrail) {
            if (e.callId().equals(callId)) {
                result.add(e);
            }
        }
        return result;
    }

    /** 客户端探测结果等接口使用的 envelope 签名密钥(测试与联调用)。 */
    public String signalingSecret() {
        return effectiveSecret;
    }

    /** 供测试/管理查看的当前 consent 聚合 */
    public P2pConsentState consentState(String callId) {
        return store.getConsent(callId);
    }

    private P2pStatusView view(String callId, long topologyGeneration) {
        String status = store.getStatus(callId);
        String fallbackReason = P2pTopologyStatus.FALLING_BACK.name().equals(status)
                ? store.getFallbackReason(callId) : null;
        P2pConsentState consent = store.getConsent(callId);
        long deadline = probeDeadlines.getOrDefault(callId, 0L);
        return new P2pStatusView(callId, topologyGeneration,
                status == null ? P2pTopologyStatus.DISABLED.name() : status,
                P2pTopologyStatus.ELIGIBLE.name().equals(status),
                List.of(),
                consent == null ? "none"
                        : consent.bothGrantedAt(now()) ? "both_valid"
                        : consent.bothGranted() ? "both_expired_or_pending" : "partial",
                fallbackReason, deadline);
    }

    private void requireEligibleOrConsented(String callId) {
        String status = requireExisting(callId);
        if (!(P2pTopologyStatus.ELIGIBLE.name().equals(status) || P2pTopologyStatus.CONSENTED.name().equals(status))) {
            throw new CallDomainException(CallErrorCode.P2P_NOT_ELIGIBLE, "consent 仅可在 ELIGIBLE/CONSENTED,当前 " + status);
        }
    }

    private String requireExisting(String callId) {
        String status = store.getStatus(callId);
        if (status == null) {
            throw new CallDomainException(CallErrorCode.SESSION_NOT_FOUND, "该通话尚无 P2P 拓扑记录,先 evaluate");
        }
        return status;
    }

    private void appendAudit(String callId, long generation, Long actor, String kind, String detail, String eventId) {
        auditTrail.add(P2pAuditEntry.of(callId, generation, actor, kind, detail, eventId, now()));
        if (auditTrail.size() > 512) {
            auditTrail.remove(0);
        }
    }
}