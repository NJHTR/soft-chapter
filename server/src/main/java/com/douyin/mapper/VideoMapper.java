package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.Video;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface VideoMapper extends BaseMapper<Video> {

    @Select("SELECT * FROM t_video WHERE is_delete = 0 AND `desc` LIKE CONCAT('%', #{keyword}, '%') ORDER BY create_time DESC LIMIT 20")
    List<Video> searchByKeyword(String keyword);

    /** 最近常看：根据用户的点赞/评论/收藏记录，找出最近互动的作者ID */
    @Select("SELECT v.author_user_id FROM (" +
            "SELECT video_id, MAX(create_time) AS create_time FROM (" +
            "  SELECT video_id, create_time FROM t_like WHERE user_id = #{userId} " +
            "  UNION ALL SELECT video_id, create_time FROM t_comment WHERE user_id = #{userId} " +
            "  UNION ALL SELECT video_id, create_time FROM t_video_collect WHERE user_id = #{userId}" +
            ") a GROUP BY video_id" +
            ") r JOIN t_video v ON v.id = r.video_id " +
            "WHERE v.author_user_id != #{userId} " +
            "GROUP BY v.author_user_id " +
            "ORDER BY MAX(r.create_time) DESC LIMIT #{limit}")
    List<Long> findRecentAuthorIds(@Param("userId") Long userId, @Param("limit") int limit);

    /** 召回用: 按品类查询视频(排除已曝光), 有内容特征的优先 */
    @Select("<script>SELECT v.* FROM t_video v " +
            "LEFT JOIN t_video_content vc ON v.id = vc.video_id " +
            "WHERE v.type IN ('recommend-video', 'image', 'text') " +
            "<if test='excludeIds != null and excludeIds.size() > 0'>" +
            "AND v.id NOT IN <foreach collection='excludeIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "</if>" +
            "<if test='minDuration != null'>AND v.duration >= #{minDuration}</if> " +
            "ORDER BY (CASE WHEN vc.extract_status = 1 THEN 0 ELSE 1 END), v.create_time DESC " +
            "LIMIT #{limit}</script>")
    List<Video> findRecallCandidates(@Param("excludeIds") List<Long> excludeIds,
                                      @Param("minDuration") Double minDuration,
                                      @Param("limit") int limit);
}
