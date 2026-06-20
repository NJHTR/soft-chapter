package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.VideoCollect;

public interface VideoCollectMapper extends BaseMapper<VideoCollect> {

    @org.apache.ibatis.annotations.Delete("DELETE FROM t_video_collect WHERE user_id = #{userId} AND video_id = #{videoId}")
    int deleteByUserAndVideo(@org.apache.ibatis.annotations.Param("userId") Long userId,
                              @org.apache.ibatis.annotations.Param("videoId") Long videoId);
}
