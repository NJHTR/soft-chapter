package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.LiveProviderSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface LiveProviderSessionMapper extends BaseMapper<LiveProviderSession> {

    @Select("SELECT id FROM t_live_room WHERE id=#{roomId} FOR UPDATE")
    Long lockLiveRoom(@Param("roomId") Long roomId);

    @Insert("INSERT INTO live_provider_session "
            + "(room_id, provider, direction, client_id, server_id, provider_session_id, stream_key, user_id, state, last_event, first_seen_at, last_seen_at, ended_at) "
            + "VALUES (#{roomId}, #{provider}, #{direction}, #{clientId}, #{serverId}, #{providerSessionId}, #{streamKey}, #{userId}, #{state}, #{lastEvent}, #{firstSeenAt}, #{lastSeenAt}, #{endedAt}) "
            + "ON DUPLICATE KEY UPDATE user_id=IF(state='ENDED', user_id, VALUES(user_id)), "
            + "state=IF(state='ENDED', state, VALUES(state)), last_event=IF(state='ENDED', last_event, VALUES(last_event)), "
            + "last_seen_at=IF(state='ENDED', last_seen_at, VALUES(last_seen_at)), ended_at=IF(state='ENDED', ended_at, NULL)")
    int upsertActive(LiveProviderSession session);

    @Select("SELECT * FROM live_provider_session WHERE provider='srs' AND direction='PUBLISH' "
            + "AND room_id=#{roomId} AND stream_key=#{streamKey} AND state='ACTIVE' "
            + "ORDER BY id DESC LIMIT 1")
    Optional<LiveProviderSession> findActivePublish(@Param("roomId") Long roomId,
                                                     @Param("streamKey") String streamKey);

    @Select("SELECT * FROM live_provider_session WHERE provider='srs' AND room_id=#{roomId} "
            + "AND direction=#{direction} AND stream_key=#{streamKey} "
            + "AND provider_session_id=#{providerSessionId} ORDER BY id DESC LIMIT 1")
    Optional<LiveProviderSession> findByGeneration(@Param("roomId") Long roomId,
                                                   @Param("direction") String direction,
                                                   @Param("streamKey") String streamKey,
                                                   @Param("providerSessionId") String providerSessionId);

    @Update("UPDATE live_provider_session SET state='ENDED', last_event=#{event}, last_seen_at=#{now}, ended_at=#{now} "
            + "WHERE room_id=#{roomId} AND provider='srs' AND direction=#{direction} AND client_id=#{clientId} "
            + "AND server_id=#{serverId} AND provider_session_id=#{providerSessionId} AND stream_key=#{streamKey} "
            + "AND state='ACTIVE'")
    int markEnded(@Param("roomId") Long roomId,
                  @Param("direction") String direction,
                  @Param("clientId") String clientId,
                  @Param("serverId") String serverId,
                  @Param("providerSessionId") String providerSessionId,
                  @Param("streamKey") String streamKey,
                  @Param("event") String event,
                  @Param("now") LocalDateTime now);

    @Update("UPDATE live_provider_session SET state='ENDED', last_event=#{event}, "
            + "last_seen_at=#{now}, ended_at=#{now} WHERE room_id=#{roomId} "
            + "AND provider='srs' AND direction='PUBLISH' AND stream_key=#{streamKey} "
            + "AND provider_session_id=#{providerSessionId} "
            + "AND state='ACTIVE'")
    int retirePublishGeneration(@Param("roomId") Long roomId,
                                @Param("streamKey") String streamKey,
                                @Param("providerSessionId") String providerSessionId,
                                @Param("event") String event,
                                @Param("now") LocalDateTime now);

    @Update("UPDATE live_provider_session SET last_seen_at=#{now}, last_event='reconcile' "
            + "WHERE room_id=#{roomId} AND provider='srs' AND direction='PUBLISH' "
            + "AND provider_session_id=#{providerSessionId} AND stream_key=#{streamKey} AND state='ACTIVE'")
    int touchActivePublish(@Param("roomId") Long roomId,
                                   @Param("providerSessionId") String providerSessionId,
                                   @Param("streamKey") String streamKey,
                                   @Param("now") LocalDateTime now);

    @Select("SELECT * FROM live_provider_session WHERE provider='srs' AND direction='PUBLISH' AND state='ACTIVE'")
    List<LiveProviderSession> findActivePublishSessions();
}
