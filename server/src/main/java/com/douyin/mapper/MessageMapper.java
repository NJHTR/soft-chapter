package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /** 搜索当前用户的消息记录，返回有匹配消息的对方用户ID（去重） */
    @Select("SELECT DISTINCT CASE WHEN from_user_id = #{userId} THEN to_user_id ELSE from_user_id END as uid " +
            "FROM t_message WHERE (from_user_id = #{userId} OR to_user_id = #{userId}) " +
            "AND content LIKE CONCAT('%', #{keyword}, '%') LIMIT 20")
    List<Long> searchChatPartners(Long userId, String keyword);

    /** 搜索通知记录，返回匹配的通知发送者用户ID（去重） */
    @Select("SELECT DISTINCT from_user_id FROM t_notification WHERE user_id = #{userId} " +
            "AND content LIKE CONCAT('%', #{keyword}, '%') LIMIT 20")
    List<Long> searchNotifSenders(Long userId, String keyword);
}
