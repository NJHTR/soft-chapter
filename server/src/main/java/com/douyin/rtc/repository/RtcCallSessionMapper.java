package com.douyin.rtc.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.rtc.domain.CallSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通话会话 Mapper (rtc-persistence)。所有状态迁移都带 expected 守卫,
 * 保证服务端是唯一状态权威,并支持乐观并发下的乱序安全。
 */
@Mapper
public interface RtcCallSessionMapper extends BaseMapper<CallSession> {

    String COLUMNS = "id,call_id,room_id,scope,mode,initiator_id,provider,state,state_version,"
            + "client_request_id,ring_at,expires_at,connected_at,ended_at,end_reason,trace_id,create_time,update_time";
    String COLUMNS_S = "s.id,s.call_id,s.room_id,s.scope,s.mode,s.initiator_id,s.provider,s.state,s.state_version,"
            + "s.client_request_id,s.ring_at,s.expires_at,s.connected_at,s.ended_at,s.end_reason,s.trace_id,"
            + "s.create_time,s.update_time";

    @Select("SELECT " + COLUMNS + " FROM rtc_call_session WHERE call_id = #{callId}")
    CallSession findByCallId(@Param("callId") String callId);

    @Select("SELECT " + COLUMNS + " FROM rtc_call_session WHERE client_request_id = #{clientRequestId}")
    CallSession findByClientRequestId(@Param("clientRequestId") String clientRequestId);

    /**
     * 查询用户当前仍占用通话能力的会话。
     *
     * <p>会话状态和参与者状态同时作为条件，避免群通话中已经离开的成员
     * 继续被误判为忙线。该查询只用于建呼前的控制面守卫，不承载媒体状态。</p>
     */
    @Select("SELECT " + COLUMNS_S + " FROM rtc_call_session s "
            + "JOIN rtc_call_participant p ON p.call_id = s.call_id "
            + "WHERE p.user_id = #{userId} "
            + "AND s.state IN ('RINGING','ACCEPTED','NEGOTIATING','CONNECTED','ENDING') "
            + "AND p.state IN ('INVITED','RINGING','JOINING','CONNECTED','RECONNECTING') "
            + "ORDER BY s.update_time DESC LIMIT 1")
    CallSession findActiveByUserId(@Param("userId") Long userId);

    /** Login/reconnect reconciliation. Expired ringing work is never resurrected. */
    @Select("SELECT " + COLUMNS_S + " FROM rtc_call_session s "
            + "JOIN rtc_call_participant p ON p.call_id = s.call_id "
            + "WHERE p.user_id = #{userId} "
            + "AND s.state IN ('RINGING','ACCEPTED','NEGOTIATING','CONNECTED','ENDING') "
            + "AND p.state IN ('INVITED','RINGING','JOINING','CONNECTED','RECONNECTING') "
            + "AND (s.expires_at IS NULL OR s.expires_at > #{now}) "
            + "ORDER BY s.update_time DESC LIMIT #{limit}")
    List<CallSession> listActiveByUserId(@Param("userId") Long userId,
                                         @Param("now") LocalDateTime now,
                                         @Param("limit") int limit);

    /** TTL worker 扫描: 指定状态且已过期的会话 */
    @Select("SELECT " + COLUMNS + " FROM rtc_call_session "
            + "WHERE state = #{state} AND expires_at IS NOT NULL AND expires_at <= #{now} "
            + "ORDER BY expires_at ASC LIMIT 200")
    List<CallSession> findExpiredBefore(@Param("state") String state, @Param("now") LocalDateTime now);

    /** TTL worker 扫描: ENDING 长期未由 webhook 收敛的会话(媒体面未建时 room_finished 不会来)。 */
    @Select("SELECT " + COLUMNS + " FROM rtc_call_session "
            + "WHERE state = 'ENDING' AND update_time <= #{olderThan} "
            + "ORDER BY update_time ASC LIMIT 200")
    List<CallSession> findEndingStuckBefore(@Param("olderThan") LocalDateTime olderThan);

    /** Bounded startup/failover recovery scan; never used as the periodic timeout scheduler. */
    @Select("SELECT " + COLUMNS + " FROM rtc_call_session WHERE id > #{afterId} "
            + "AND state IN ('RINGING','NEGOTIATING') AND expires_at IS NOT NULL "
            + "ORDER BY id ASC LIMIT #{limit}")
    List<CallSession> findTimeoutRecoveryBatch(@Param("afterId") Long afterId, @Param("limit") int limit);

    @Select("SELECT call_id FROM rtc_call_session "
            + "WHERE state IN ('REJECTED','CANCELLED','EXPIRED','FAILED','ENDED') "
            + "AND COALESCE(ended_at, update_time) < #{olderThan} ORDER BY id ASC LIMIT #{limit}")
    List<String> findTerminalCallIdsForPurge(@Param("olderThan") LocalDateTime olderThan,
                                             @Param("limit") int limit);

    @Delete({"<script>",
            "DELETE FROM rtc_call_session WHERE call_id IN",
            "<foreach collection='callIds' item='callId' open='(' separator=',' close=')'>#{callId}</foreach>",
            "AND state IN ('REJECTED','CANCELLED','EXPIRED','FAILED','ENDED')",
            "AND COALESCE(ended_at, update_time) &lt; #{olderThan}",
            "</script>"})
    int deleteTerminalByCallIds(@Param("callIds") List<String> callIds,
                                @Param("olderThan") LocalDateTime olderThan);

    /**
     * 守卫更新: 仅当当前状态等于 expected 时迁移到 target。
     * end_reason/ended_at/expires_at 直接写入;connected_at 用 COALESCE 保留首次接通时间。
     *
     * @return 受影响行数,0 表示状态已被并发修改(调用方应重新加载)
     */
    @Update("UPDATE rtc_call_session SET state = #{target}, end_reason = #{endReason}, "
            + "ended_at = #{endedAt}, connected_at = COALESCE(#{connectedAt}, connected_at), "
            + "expires_at = #{expiresAt}, "
            + "ring_at = CASE WHEN #{target} = 'RINGING' THEN COALESCE(ring_at, NOW()) ELSE ring_at END, "
            + "state_version = state_version + 1 "
            + "WHERE call_id = #{callId} AND state = #{expected} AND state_version = #{expectedVersion}")
    int transitionSession(@Param("callId") String callId,
                          @Param("expected") String expected,
                          @Param("target") String target,
                          @Param("endReason") String endReason,
                          @Param("endedAt") LocalDateTime endedAt,
                          @Param("connectedAt") LocalDateTime connectedAt,
                          @Param("expiresAt") LocalDateTime expiresAt,
                          @Param("expectedVersion") Long expectedVersion);
}
