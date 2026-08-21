package com.douyin.rtc.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.rtc.domain.CallDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;
import java.util.List;

/** Durable per-device call state. */
@Mapper
public interface RtcCallDeviceMapper extends BaseMapper<CallDevice> {
    String COLUMNS = "id,call_id,user_id,device_id,state,accepted_at,rejected_at,create_time,update_time";

    @Select("SELECT " + COLUMNS + " FROM rtc_call_device WHERE call_id = #{callId} AND user_id = #{userId} AND device_id = #{deviceId}")
    CallDevice findByCallUserDevice(@Param("callId") String callId, @Param("userId") Long userId,
                                    @Param("deviceId") String deviceId);

    @Select("SELECT " + COLUMNS + " FROM rtc_call_device WHERE call_id = #{callId} ORDER BY id ASC")
    List<CallDevice> listByCall(@Param("callId") String callId);

    @Select("SELECT " + COLUMNS + " FROM rtc_call_device WHERE call_id = #{callId} AND user_id = #{userId} ORDER BY id ASC")
    List<CallDevice> listByCallAndUser(@Param("callId") String callId, @Param("userId") Long userId);

    @Update("UPDATE rtc_call_device SET state = #{target}, accepted_at = COALESCE(#{acceptedAt}, accepted_at), "
            + "rejected_at = COALESCE(#{rejectedAt}, rejected_at) "
            + "WHERE call_id = #{callId} AND user_id = #{userId} AND device_id = #{deviceId} AND state = #{expected}")
    int transition(@Param("callId") String callId, @Param("userId") Long userId,
                   @Param("deviceId") String deviceId, @Param("expected") String expected,
                   @Param("target") String target, @Param("acceptedAt") LocalDateTime acceptedAt,
                   @Param("rejectedAt") LocalDateTime rejectedAt);

    /** Terminal session cleanup: cancel all pending deliveries atomically. */
    @Update("UPDATE rtc_call_device SET state = 'CANCELLED' "
            + "WHERE call_id = #{callId} AND state IN ('RINGING', 'INVITED')")
    int cancelRingingByCall(@Param("callId") String callId);

    @Delete({"<script>", "DELETE FROM rtc_call_device WHERE call_id IN",
            "<foreach collection='callIds' item='callId' open='(' separator=',' close=')'>#{callId}</foreach>",
            "</script>"})
    int deleteByCallIds(@Param("callIds") List<String> callIds);
}
