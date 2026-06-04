package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.Video;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface VideoMapper extends BaseMapper<Video> {

    @org.apache.ibatis.annotations.Select("SELECT * FROM t_video WHERE is_delete = 0 AND `desc` LIKE CONCAT('%', #{keyword}, '%') ORDER BY create_time DESC LIMIT 20")
    List<Video> searchByKeyword(String keyword);

    /** 最近常看：根据用户的点赞/评论/收藏记录，找出最近互动的作者ID */
    @org.apache.ibatis.annotations.Select("SELECT DISTINCT v.author_user_id FROM (" +
            "SELECT video_id, create_time FROM t_like WHERE user_id = #{userId} " +
            "UNION ALL SELECT video_id, create_time FROM t_comment WHERE user_id = #{userId} " +
            "UNION ALL SELECT video_id, create_time FROM t_video_collect WHERE user_id = #{userId}" +
            ") r JOIN t_video v ON v.id = r.video_id " +
            "WHERE v.author_user_id != #{userId} " +
            "ORDER BY r.create_time DESC LIMIT #{limit}")
    List<Long> findRecentAuthorIds(@Param("userId") Long userId, @Param("limit") int limit);
}
