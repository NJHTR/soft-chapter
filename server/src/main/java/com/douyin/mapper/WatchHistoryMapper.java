package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.WatchHistory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface WatchHistoryMapper extends BaseMapper<WatchHistory> {

    /** 获取用户观看历史视频ID，按最近观看时间排序 */
    @Select("SELECT video_id FROM t_watch_history WHERE user_id = #{userId} ORDER BY update_time DESC LIMIT #{offset}, #{limit}")
    List<Long> findHistoryVideoIds(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    /** 批量统计候选视频的近期观看人次 */
    @Select("<script>SELECT video_id, COUNT(*) as cnt FROM t_watch_history " +
            "WHERE create_time >= #{since} " +
            "AND video_id IN <foreach collection='videoIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY video_id</script>")
    List<java.util.Map<String, Object>> countRecentWatches(@Param("videoIds") List<Long> videoIds,
                                                            @Param("since") java.time.LocalDateTime since);

    /** 用户近期快速划走的视频ID (用于品类负反馈) */
    @Select("SELECT video_id FROM t_watch_history " +
            "WHERE user_id = #{userId} AND swipe_seconds < #{maxSwipeSeconds} " +
            "AND create_time >= #{since}")
    List<Long> findQuickSkipVideoIds(@Param("userId") Long userId,
                                      @Param("maxSwipeSeconds") double maxSwipeSeconds,
                                      @Param("since") java.time.LocalDateTime since);

    /** 批量计算候选视频的全站平均完播率 */
    @Select("<script>SELECT video_id, " +
            "AVG(CASE WHEN video_duration > 0 THEN watch_duration / video_duration ELSE 0 END) as avg_comp " +
            "FROM t_watch_history " +
            "WHERE video_id IN <foreach collection='videoIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY video_id</script>")
    List<java.util.Map<String, Object>> avgCompletionRate(@Param("videoIds") List<Long> videoIds);

    /** 找到最近看过指定视频的用户ID (协同搜索) */
    @Select("SELECT DISTINCT user_id FROM t_watch_history " +
            "WHERE video_id = #{videoId} AND create_time >= #{since} " +
            "LIMIT #{limit}")
    List<Long> findRecentWatchersOfVideo(@Param("videoId") Long videoId,
                                          @Param("since") java.time.LocalDateTime since,
                                          @Param("limit") int limit);
}
