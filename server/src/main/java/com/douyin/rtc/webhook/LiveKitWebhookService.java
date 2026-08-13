package com.douyin.rtc.webhook;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallParticipant;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.domain.CallState;
import com.douyin.rtc.service.CallService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * LiveKit webhook 事件消费 (rtc-media-adapter)。
 *
 * <p>幂等: 事件先 INSERT IGNORE 落 rtc_webhook_ledger(event_id 唯一),
 * 0 行=重放/重复,直接返回 200 幂等响应,不再执行状态动作。
 *
 * <p>事件 → CallState 映射(宪法 CALL_DOMAIN_MODEL.md §2 转移表):
 * <pre>
 * room_started / room_created  → confirmConnected  (NEGOTIATING|connected|CONNECTED, provider webhook 确认)
 * participant_joined           → joinCall          (参与者 RINGING→JOINING)
 * participant_left             → leaveCall         (JOINING/CONNECTED/RECONNECTING→LEFT)
 * room_finished                → hangup + confirmEnded
 *                                   CONNECTED|hangup|ENDING → ENDING|ended|ENDED(webhook 收敛)
 *                                   NEGOTIATING|hangup|ENDED(媒体面空即未接通结束)
 * 其他事件(track_*、egress_*、ingress_* 等)→ 只留账本审计,不推进状态(宪法 §2 未列出转换禁止隐式修正)
 * </pre>
 *
 * <p>媒体面事件只做状态收敛提示;成员关系仍以服务端 roster 为准,
 * 非 roster 参与者(join/leave 抛 NOT_AUTHORIZED)仅记录日志不阻断。
 */
@Slf4j
@Service
public class LiveKitWebhookService {

    private static final String CONVERGE_SUFFIX = ":converge";

    private final RtcWebhookLedgerMapper ledgerMapper;
    private final CallService callService;

    public LiveKitWebhookService(RtcWebhookLedgerMapper ledgerMapper, CallService callService) {
        this.ledgerMapper = ledgerMapper;
        this.callService = callService;
    }

    /**
     * 消费一个 webhook 事件。event_id 重复时返回 duplicate=true(幂等)。
     *
     * @param rawJson 签名校验通过的原始 body,落账本留审计
     */
    public WebhookHandleResult handle(String eventId, String eventType, String roomName,
                                      Map<String, Object> payload, String rawJson) {
        if (eventId == null || eventId.isBlank() || eventType == null || eventType.isBlank()) {
            return new WebhookHandleResult(false, false, "missing event id/type");
        }
        boolean recorded = record(eventId, eventType, roomName, rawJson);
        if (!recorded) {
            log.info("[RTC-WEBHOOK] duplicate ignored: eventId={} type={}", eventId, eventType);
            return new WebhookHandleResult(true, true, "duplicate");
        }
        return new WebhookHandleResult(apply(eventId, eventType, payload), false, null);
    }

    public record WebhookHandleResult(boolean handled, boolean duplicate, String reason) {
    }

    /** 幂等落账本;event_id 已存在(重放)返回 false。 */
    private boolean record(String eventId, String eventType, String roomName, String rawJson) {
        RtcWebhookLedger ledger = new RtcWebhookLedger();
        ledger.setId(IdWorker.getId());
        ledger.setEventId(eventId);
        ledger.setCallId(roomName);
        ledger.setEventType(eventType);
        ledger.setPayload(rawJson);
        ledger.setReceivedAt(LocalDateTime.now());
        ledger.setProcessed(1);
        return ledgerMapper.insertIgnore(ledger) > 0;
    }

    private boolean apply(String eventId, String eventType, Map<String, Object> payload) {
        String roomName = roomNameOf(payload);
        CallSession session = callService.getCall(roomName);
        if (session == null) {
            // 房间名由服务端确定性派生(callId 或既有 room_id),未知房间只留审计
            log.warn("[RTC-WEBHOOK] unknown room, audit only: eventId={} type={} room={}", eventId, eventType, roomName);
            return true;
        }
        try {
            switch (eventType) {
                case "room_started", "room_created" -> confirmConnected(eventId, session);
                case "participant_joined" -> callService.joinCall(session.getCallId(), actorFrom(payload),
                        eventId, eventId);
                case "participant_left" -> callService.leaveCall(session.getCallId(), actorFrom(payload),
                        eventId, eventId);
                case "room_finished" -> finish(eventId, session);
                default -> log.debug("[RTC-WEBHOOK] no state mapping: eventId={} type={}", eventId, eventType);
            }
        } catch (CallDomainException e) {
            // 领域拒绝(如非 roster 参与者 join/leave): 账本已记,不推进状态,不 500
            log.warn("[RTC-WEBHOOK] domain rejection (ledger kept, no state change): eventId={} type={} code={} msg={}",
                    eventId, eventType, e.getCode(), e.getMessage());
        }
        return true;
    }

    /** webhook 负载里的房间名(payload.room.name) */
    private String roomNameOf(Map<String, Object> payload) {
        Object room = payload.get("room");
        if (room instanceof Map<?, ?> m && m.get("name") != null) {
            return String.valueOf(m.get("name"));
        }
        return null;
    }

    /** room_started/room_created: 媒体面就位,由 provider webhook 确认 NEGOTIATING→CONNECTED(宪法 §2) */
    private void confirmConnected(String eventId, CallSession session) {
        callService.confirmConnected(session.getCallId(), systemActor(session), eventId, eventId);
    }

    /**
     * room_finished: 媒体面已空。等价于最后在场的参与者挂断:
     * CONNECTED→ENDING(宪法 §2 CONNECTED|hangup|ENDING),随后 ENDING→ENDED
     * (宪法 §2 ENDING|ended|ENDED, provider/webhook 收敛);NEGOTIATING 直接
     * hangup→ENDED(宪法 §2 NEGOTIATING|hangup|ENDED)。
     * 收敛事件用派生 event_id+":converge",避免与 hangup 同 event_id 被账本判重。
     */
    private void finish(String eventId, CallSession session) {
        CallState state = CallState.valueOf(session.getState());
        String callId = session.getCallId();
        Long actor = systemActor(session);
        if (state == CallState.ENDING) {
            callService.confirmEnded(callId, eventId + CONVERGE_SUFFIX, eventId);
        } else if (state == CallState.CONNECTED) {
            callService.hangupCall(callId, actor, eventId, eventId);
            callService.confirmEnded(callId, eventId + CONVERGE_SUFFIX, eventId);
        } else if (state == CallState.NEGOTIATING) {
            callService.hangupCall(callId, actor, eventId, eventId);
        }
        // 其他状态(RINGING/ACCEPTED/终态): 媒体面不应存在,仅审计
    }

    /** 事件关联的参与人: 优先事件 payload participant.identity,否则 null(调用方按 roster 守卫) */
    private Long actorFrom(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object p = payload.get("participant");
        if (p instanceof Map<?, ?> m) {
            Object identity = m.get("identity");
            if (identity != null) {
                try {
                    return Long.valueOf(String.valueOf(identity));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /** 兜底执行人: 最后在场的 roster 参与者,否则发起者(宪法: 服务端权威) */
    private Long systemActor(CallSession session) {
        for (CallParticipant p : callService.participants(session.getCallId())) {
            String s = p.getState();
            if ("JOINING".equals(s) || "CONNECTED".equals(s) || "RECONNECTING".equals(s)) {
                return p.getUserId();
            }
        }
        return session.getInitiatorId();
    }
}