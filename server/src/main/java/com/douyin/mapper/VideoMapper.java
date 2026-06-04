package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.Video;

import java.util.List;

public interface VideoMapper extends BaseMapper<Video> {

    @org.apache.ibatis.annotations.Select("SELECT * FROM t_video WHERE is_delete = 0 AND `desc` LIKE CONCAT('%', #{keyword}, '%') ORDER BY create_time DESC LIMIT 20")
    List<Video> searchByKeyword(String keyword);
}
