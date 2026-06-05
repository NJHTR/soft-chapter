package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.Like;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface LikeMapper extends BaseMapper<Like> {

    /** 协同过滤: 找到与用户已赞视频共同被赞的视频 */
    @Select("<script>SELECT l2.video_id, COUNT(*) as co_count FROM t_like l1 " +
            "JOIN t_like l2 ON l1.user_id = l2.user_id AND l1.video_id != l2.video_id " +
            "WHERE l1.video_id IN <foreach collection='videoIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "<if test='excludeIds != null and excludeIds.size() > 0'>" +
            "AND l2.video_id NOT IN <foreach collection='excludeIds' item='eid' open='(' separator=',' close=')'>#{eid}</foreach> " +
            "</if>" +
            "GROUP BY l2.video_id ORDER BY co_count DESC LIMIT #{limit}</script>")
    List<Map<String, Object>> findCoLikedVideoIds(@Param("videoIds") List<Long> videoIds,
                                                   @Param("excludeIds") List<Long> excludeIds,
                                                   @Param("limit") int limit);

    /** 获取用户最近点赞的视频ID列表 */
    @Select("SELECT video_id FROM t_like WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT #{limit}")
    List<Long> findRecentLikedVideoIds(@Param("userId") Long userId, @Param("limit") int limit);
}
