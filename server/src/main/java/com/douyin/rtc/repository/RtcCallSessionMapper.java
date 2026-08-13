package com.douyin.rtc.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.rtc.domain.CallSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通话会话 Mapper (rtc-persistence)。所有状态迁移都带 expected 守卫,
 * 保证服务端是唯一状态权威,并支持乐观并发下的乱序安全。
 */
@Mapper
public interface RtcCallSessionMapper extends BaseMapper<CallSession> {

    @Select("SELECT * FROM rtc_call_session WHERE call_id = #{callId}")
    CallSession findByCallId(@Param("callId") String callId);

    @Select("SELECT * FROM rtc_call_session WHERE client_request_id = #{clientRequestId}")
    CallSession findByClientRequestId(@Param("clientRequestId") String clientRequestId);

    /** TTL worker 扫描: 指定状态且已过期的会话 */
    @Select("SELECT * FROM rtc_call_session WHERE state = #{state} AND expires_at IS NOT NULL AND expires_at <= #{now} LIMIT 200")
    List<CallSession> findExpiredBefore(@Param("state") String state, @Param("now") LocalDateTime now);

    /**
     * 守卫更新: 仅当当前状态等于 expected 时迁移到 target。
     * end_reason/ended_at/expires_at 直接写入;connected_at 用 COALESCE 保留首次接通时间。
     *
     * @return 受影响行数,0 表示状态已被并发修改(调用方应重新加载)
     */
    @Update("UPDATE rtc_call_session SET state = #{target}, end_reason = #{endReason}, "
            + "ended_at = #{endedAt}, connected_at = COALESCE(#{connectedAt}, connected_at), "
            + "expires_at = #{expiresAt} "
            + "WHERE call_id = #{callId} AND state = #{expected}")
    int transitionSession(@Param("callId") String callId,
                          @Param("expected") String expected,
                          @Param("target") String target,
                          @Param("endReason") String endReason,
                          @Param("endedAt") LocalDateTime endedAt,
                          @Param("connectedAt") LocalDateTime connectedAt,
                          @Param("expiresAt") LocalDateTime expiresAt);
}