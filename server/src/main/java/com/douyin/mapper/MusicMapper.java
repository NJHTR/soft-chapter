package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.Music;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface MusicMapper extends BaseMapper<Music> {

    @Select("SELECT * FROM t_music WHERE source = #{source} AND source_id = #{sourceId} LIMIT 1")
    Music findBySource(@Param("source") String source, @Param("sourceId") Long sourceId);

    @Select("SELECT * FROM t_music WHERE name LIKE CONCAT('%', #{keyword}, '%') OR artist LIKE CONCAT('%', #{keyword}, '%') ORDER BY id DESC LIMIT #{limit}")
    List<Music> search(@Param("keyword") String keyword, @Param("limit") int limit);
}
