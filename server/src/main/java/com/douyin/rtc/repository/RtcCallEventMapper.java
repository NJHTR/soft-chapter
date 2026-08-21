package com.douyin.rtc.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.rtc.domain.CallEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 通话事件账本 Mapper (rtc-persistence)。
 * INSERT IGNORE + event_id 唯一索引保证全局幂等;
 * (call_id, participant_id, seq) 复合索引保证 seq 单调查询。
 */
@Mapper
public interface RtcCallEventMapper extends BaseMapper<CallEvent> {

    String COLUMNS = "id,event_id,call_id,participant_id,kind,seq,event_version,occurred_at,payload,trace_id";

    /**
     * 幂等写入事件。event_id 重复时返回 0(调用方按重放处理)。
     */
    @Insert("INSERT IGNORE INTO rtc_call_event "
            + "(id, event_id, call_id, participant_id, kind, seq, event_version, occurred_at, payload, trace_id) "
            + "VALUES (#{id}, #{eventId}, #{callId}, #{participantId}, #{kind}, #{seq}, #{eventVersion}, #{occurredAt}, #{payload}, #{traceId})")
    int insertIgnore(CallEvent event);

    @Select("SELECT COUNT(*) > 0 FROM rtc_call_event WHERE event_id = #{eventId}")
    boolean existsByEventId(@Param("eventId") String eventId);

    @Select("SELECT " + COLUMNS + " FROM rtc_call_event WHERE event_id = #{eventId}")
    CallEvent findByEventId(@Param("eventId") String eventId);

    @Select("SELECT COALESCE(MAX(seq), 0) FROM rtc_call_event WHERE call_id = #{callId} AND participant_id = #{participantId}")
    long maxSeq(@Param("callId") String callId, @Param("participantId") Long participantId);

    @Select("SELECT " + COLUMNS + " FROM rtc_call_event WHERE call_id = #{callId} ORDER BY seq ASC LIMIT 1000")
    List<CallEvent> listByCall(@Param("callId") String callId);

    @Select("SELECT " + COLUMNS + " FROM rtc_call_event WHERE call_id = #{callId} "
            + "AND participant_id = #{participantId} ORDER BY seq ASC LIMIT 1000")
    List<CallEvent> listByCallAndParticipant(@Param("callId") String callId, @Param("participantId") Long participantId);
}
