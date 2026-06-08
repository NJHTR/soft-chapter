package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.Group;
import com.douyin.entity.GroupMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GroupMessageMapper extends BaseMapper<GroupMessage> {

    @Select("SELECT DISTINCT g.id, g.name, g.avatar, g.member_count " +
            "FROM t_group g INNER JOIN t_group_member gm ON g.id = gm.group_id " +
            "WHERE gm.user_id = #{userId} AND g.name LIKE CONCAT('%', #{keyword}, '%') " +
            "LIMIT 20")
    List<Group> searchGroups(Long userId, String keyword);
}
