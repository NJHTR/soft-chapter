package com.douyin.rtc.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.douyin.entity.Message;
import com.douyin.rtc.domain.CallEvent;
import com.douyin.rtc.domain.CallEventKind;
import com.douyin.rtc.domain.CallJson;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.repository.RtcCallEventMapper;
import com.douyin.rtc.repository.RtcMessageProjectionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 通话事件账本 (rtc-persistence 真相) 与旧消息兼容投影。
 *
 * - CallEvent 写入: event_id 全局幂等(INSERT IGNORE + 唯一索引),
 *   同一 (call_id, participant_id) 的 seq 单调递增。
 * - 兼容投影: CallService 落库后同步写一条带 call_id 的 t_message
 *   (msg_type=10 音频 / 11 视频),extra 保留 callState/duration,
 *   新增 call_id 走向后兼容 JSON 扩展,不新增 t_message 列。
 *   该投影属于 chat-persistence 领域,按宪法仅作投影使用。
 */
@Slf4j
@Service
public class CallLedgerService {

    private static final long SYSTEM_PARTICIPANT = 0L;

    private final RtcCallEventMapper eventMapper;
    private final RtcMessageProjectionMapper projectionMapper;

    public CallLedgerService(RtcCallEventMapper eventMapper, RtcMessageProjectionMapper projectionMapper) {
        this.eventMapper = eventMapper;
        this.projectionMapper = projectionMapper;
    }

    /**
     * 追加一条事件,seq = 该 (call_id, participant_id) 当前最大值 + 1。
     *
     * @return 写入成功返回事件;event_id 重复(重放)返回 null
     */
    public CallEvent append(String eventId, String callId, Long participantId, CallEventKind kind,
                            Map<String, ?> payload, String traceId) {
        return append(eventId, callId, participantId, kind, 0L, payload, traceId);
    }

    public CallEvent append(String eventId, String callId, Long participantId, CallEventKind kind,
                            Long eventVersion, Map<String, ?> payload, String traceId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("event_id 不能为空");
        }
        long pid = participantId == null ? SYSTEM_PARTICIPANT : participantId;
        long seq = eventMapper.maxSeq(callId, pid) + 1;
        CallEvent event = new CallEvent();
        event.setId(IdWorker.getId());
        event.setEventId(eventId);
        event.setCallId(callId);
        event.setParticipantId(pid);
        event.setKind(kind.getWire());
        event.setSeq(seq);
        event.setEventVersion(eventVersion == null ? 0L : eventVersion);
        event.setOccurredAt(LocalDateTime.now());
        event.setPayload(payload == null || payload.isEmpty() ? null : CallJson.write(payload));
        event.setTraceId(traceId);
        int rows = eventMapper.insertIgnore(event);
        if (rows == 0) {
            log.info("[CALL-LEDGER] event replay ignored: eventId={} callId={} kind={}", eventId, callId, kind.getWire());
            return null;
        }
        return event;
    }

    public boolean isReplay(String eventId) {
        return eventId != null && !eventId.isBlank() && eventMapper.existsByEventId(eventId);
    }

    public CallEvent findByEventId(String eventId) {
        return eventMapper.findByEventId(eventId);
    }

    public List<CallEvent> eventsByCall(String callId) {
        return eventMapper.listByCall(callId);
    }

    public List<CallEvent> eventsByCallAndParticipant(String callId, Long participantId) {
        return eventMapper.listByCallAndParticipant(callId, participantId);
    }

    /**
     * 兼容投影写 t_message。仅 direct 通话(1 对 1 消息表语义),
     * 群通话投影走 t_group_message,属后续任务范围。
     * 投影失败不阻断主流程(chat-persistence 只是投影)。
     */
    public void projectCallMessage(CallSession session, Long calleeId, int callState, long durationSeconds) {
        try {
            if (!"direct".equals(session.getScope())) {
                return;
            }
            Long from = session.getInitiatorId();
            Long to = calleeId;
            if (from == null || to == null || from.equals(to)) {
                return;
            }
            int msgType = "video".equals(session.getMode()) ? 11 : 10;
            String extra = CallJson.write(Map.of(
                    "callState", callState,
                    "duration", durationSeconds,
                    "call_id", session.getCallId()));
            Message message = new Message();
            message.setId(IdWorker.getId());
            message.setFromUserId(from);
            message.setToUserId(to);
            message.setContent("");
            message.setMsgType(msgType);
            message.setExtra(extra);
            message.setIsRead(0);
            projectionMapper.insertProjection(message);
        } catch (Exception e) {
            log.warn("[CALL-LEDGER] 兼容投影写入失败 callId={}: {}", session.getCallId(), e.getMessage());
        }
    }

    /** 查询投影: 读取 t_message 中带 call_id 的兼容记录,并回填 call_id */
    public List<CompatCallProjection> projectionsByCall(String callId) {
        return projectionMapper.findCallProjections(callId).stream()
                .map(m -> CompatCallProjection.from(m, callId))
                .toList();
    }

    /** 通话时长(秒),未接通返回 0 */
    public long durationSeconds(CallSession session) {
        if (session.getConnectedAt() == null || session.getEndedAt() == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(session.getConnectedAt(), session.getEndedAt()).toSeconds());
    }
}
