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
}
