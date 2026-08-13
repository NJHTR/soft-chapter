package com.douyin.rtc.repository;

import com.douyin.entity.Message;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 旧消息兼容投影 Mapper (chat-persistence 领域只做投影)。
 * rtc 模块落库后把带 call_id 的记录写入 t_message(msg_type=10/11),
 * extra 内保留 callState/duration 并新增 call_id,不新增任何列。
 */
@Mapper
public interface RtcMessageProjectionMapper {

    @Insert("INSERT INTO t_message (id, from_user_id, to_user_id, content, msg_type, extra, is_read, create_time) "
            + "VALUES (#{id}, #{fromUserId}, #{toUserId}, #{content}, #{msgType}, #{extra}, #{isRead}, NOW())")
    int insertProjection(Message message);

    /** 查询某 call_id 的兼容投影记录(extra JSON 内含 call_id) */
    @Select("SELECT * FROM t_message WHERE msg_type IN (10, 11) "
            + "AND extra LIKE CONCAT('%\\\"call_id\\\":\\\"', #{callId}, '\\\"%') ORDER BY id DESC LIMIT 100")
    List<Message> findCallProjections(@Param("callId") String callId);
}