package com.douyin.rtc.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.rtc.domain.CallParticipant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通话参与者 Mapper (rtc-persistence)。状态迁移同样带 expected 守卫。
 */
@Mapper
public interface RtcCallParticipantMapper extends BaseMapper<CallParticipant> {

    String COLUMNS = "id,call_id,user_id,role,state,joined_at,left_at,reason,profile_snapshot,create_time,update_time";

    @Select("SELECT " + COLUMNS + " FROM rtc_call_participant WHERE call_id = #{callId} AND user_id = #{userId}")
    CallParticipant findByCallAndUser(@Param("callId") String callId, @Param("userId") Long userId);

    @Select("SELECT " + COLUMNS + " FROM rtc_call_participant WHERE call_id = #{callId} ORDER BY id ASC")
    List<CallParticipant> listByCall(@Param("callId") String callId);

    /**
     * 守卫更新参与者状态。reason/joined_at/left_at 用 COALESCE 保留历史值。
     *
     * @return 受影响行数,0 表示参与者不在 expected 状态(乱序/已被并发修改)
     */
    @Update("UPDATE rtc_call_participant SET state = #{target}, "
            + "reason = COALESCE(#{reason}, reason), "
            + "joined_at = COALESCE(#{joinedAt}, joined_at), "
            + "left_at = COALESCE(#{leftAt}, left_at) "
            + "WHERE call_id = #{callId} AND user_id = #{userId} AND state = #{expected}")
    int transitionParticipant(@Param("callId") String callId,
                              @Param("userId") Long userId,
                              @Param("expected") String expected,
                              @Param("target") String target,
                              @Param("reason") String reason,
                              @Param("joinedAt") LocalDateTime joinedAt,
                              @Param("leftAt") LocalDateTime leftAt);
}
