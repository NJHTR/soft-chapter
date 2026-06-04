package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.User;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM t_user WHERE is_delete = 0 ORDER BY RAND() LIMIT 30")
    List<User> selectRandomFriends();

    @Select("SELECT * FROM t_user WHERE is_delete = 0 AND (nickname LIKE CONCAT('%', #{keyword}, '%') OR unique_id LIKE CONCAT('%', #{keyword}, '%')) LIMIT 20")
    List<User> searchByKeyword(String keyword);
}
