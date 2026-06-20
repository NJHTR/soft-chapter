package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.Video;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface VideoMapper extends BaseMapper<Video> {

    @Select("SELECT * FROM t_video WHERE is_delete = 0 AND status = 'APPROVED' AND `desc` LIKE CONCAT('%', #{keyword}, '%') ORDER BY create_time DESC LIMIT 20")
    List<Video> searchByKeyword(String keyword);

    /**
     * 多字段加权搜索: JOIN t_video_content, 在分类/关键词/标签/描述等多字段上匹配
     *
     * 计分规则 (单关键词):
     *   text_category 精确匹配 = 10, keywords JSON 命中 = 8,
     *   text_category 模糊 = 7, object_tags = 5, scene_tags = 3,
     *   desc LIKE = 2, visual_desc LIKE = 1
     *
     * @param categories 目标分类列表 (精确匹配权重最高)
     * @param keywords   目标关键词/搜索词列表 (至少含原始搜索词)
     * @param limit      返回数量上限
     */
    @Select("<script>" +
            "SELECT v.*, (" +
            "<foreach collection='keywords' item='kw' separator='+'>" +
            " CASE WHEN vc.text_category LIKE CONCAT('%', #{kw}, '%') THEN 7 ELSE 0 END +" +
            " CASE WHEN JSON_CONTAINS(vc.keywords, JSON_QUOTE(#{kw})) THEN 8 ELSE 0 END +" +
            " CASE WHEN JSON_CONTAINS(vc.object_tags, JSON_QUOTE(#{kw})) THEN 5 ELSE 0 END +" +
            " CASE WHEN JSON_CONTAINS(vc.scene_tags, JSON_QUOTE(#{kw})) THEN 3 ELSE 0 END +" +
            " CASE WHEN v.`desc` LIKE CONCAT('%', #{kw}, '%') THEN 2 ELSE 0 END +" +
            " CASE WHEN vc.visual_desc LIKE CONCAT('%', #{kw}, '%') THEN 1 ELSE 0 END +" +
            "</foreach>" +
            "<foreach collection='categories' item='cat' separator='+'>" +
            " CASE WHEN vc.text_category = #{cat} THEN 10 ELSE 0 END +" +
            "</foreach>" +
            " 0) AS relevance_score " +
            "FROM t_video v " +
            "LEFT JOIN t_video_content vc ON v.id = vc.video_id " +
            "WHERE v.is_delete = 0 AND v.status = 'APPROVED' " +
            "AND (" +
            "  <foreach collection='keywords' item='kw' separator=' OR '>" +
            "    vc.text_category LIKE CONCAT('%', #{kw}, '%') " +
            "    OR JSON_CONTAINS(vc.keywords, JSON_QUOTE(#{kw})) " +
            "    OR v.`desc` LIKE CONCAT('%', #{kw}, '%') " +
            "  </foreach>" +
            "  <if test='keywords.size() > 0 and categories.size() > 0'> OR </if>" +
            "  <foreach collection='categories' item='cat' separator=' OR '>" +
            "    vc.text_category = #{cat}" +
            "  </foreach>" +
            ")" +
            "HAVING relevance_score > 0 " +
            "ORDER BY relevance_score DESC, v.like_count DESC " +
            "LIMIT #{limit}" +
            "</script>")
    List<Video> searchByKeywords(@org.apache.ibatis.annotations.Param("categories") List<String> categories,
                                 @org.apache.ibatis.annotations.Param("keywords") List<String> keywords,
                                 @org.apache.ibatis.annotations.Param("limit") int limit);

    /** 最近常看：根据用户的点赞/评论/收藏记录，找出最近互动的作者ID */
    @Select("SELECT v.author_user_id FROM (" +
            "SELECT video_id, MAX(create_time) AS create_time FROM (" +
            "  SELECT video_id, create_time FROM t_like WHERE user_id = #{userId} " +
            "  UNION ALL SELECT video_id, create_time FROM t_comment WHERE user_id = #{userId} " +
            "  UNION ALL SELECT video_id, create_time FROM t_video_collect WHERE user_id = #{userId}" +
            ") a GROUP BY video_id" +
            ") r JOIN t_video v ON v.id = r.video_id " +
            "WHERE v.status = 'APPROVED' AND v.is_delete = 0 AND v.author_user_id != #{userId} " +
            "GROUP BY v.author_user_id " +
            "ORDER BY MAX(r.create_time) DESC LIMIT #{limit}")
    List<Long> findRecentAuthorIds(@Param("userId") Long userId, @Param("limit") int limit);

    /** 召回用: 按品类查询视频(排除已曝光), 有内容特征的优先 */
    @Select("<script>SELECT v.* FROM t_video v " +
            "LEFT JOIN t_video_content vc ON v.id = vc.video_id " +
            "WHERE v.status = 'APPROVED' AND v.is_delete = 0 AND v.type IN ('recommend-video', 'image', 'text') " +
            "<if test='excludeIds != null and excludeIds.size() > 0'>" +
            "AND v.id NOT IN <foreach collection='excludeIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "</if>" +
            "<if test='minDuration != null'>AND v.duration >= #{minDuration}</if> " +
            "ORDER BY (CASE WHEN vc.extract_status = 1 THEN 0 ELSE 1 END), v.create_time DESC " +
            "LIMIT #{limit}</script>")
    List<Video> findRecallCandidates(@Param("excludeIds") List<Long> excludeIds,
                                      @Param("minDuration") Double minDuration,
                                      @Param("limit") int limit);

    /** 原子递增/递减点赞数 */
    @org.apache.ibatis.annotations.Update("UPDATE t_video SET like_count = GREATEST(0, like_count + #{delta}) WHERE id = #{videoId}")
    int incrementLike(@Param("videoId") Long videoId, @Param("delta") int delta);

    /** 原子递增/递减收藏数 */
    @org.apache.ibatis.annotations.Update("UPDATE t_video SET collect_count = GREATEST(0, collect_count + #{delta}) WHERE id = #{videoId}")
    int incrementCollect(@Param("videoId") Long videoId, @Param("delta") int delta);
}
