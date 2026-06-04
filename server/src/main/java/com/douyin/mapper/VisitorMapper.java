package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.Visitor;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface VisitorMapper extends BaseMapper<Visitor> {

    /** 查某用户的访客列表，按最近访问时间排序，去重(每人只保留最近一次) */
    @Select("SELECT DISTINCT visitor_id, MAX(create_time) as last_time FROM t_visitor " +
            "WHERE user_id = #{userId} AND visitor_id != #{userId} " +
            "GROUP BY visitor_id ORDER BY last_time DESC LIMIT #{limit}")
    List<java.util.Map<String, Object>> getRecentVisitors(@Param("userId") Long userId, @Param("limit") int limit);
}
