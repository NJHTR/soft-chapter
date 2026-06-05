package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.LoginHistory;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface LoginHistoryMapper extends BaseMapper<LoginHistory> {

    @Select("SELECT * FROM t_login_history WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT #{offset}, #{limit}")
    List<LoginHistory> findByUserId(Long userId, int offset, int limit);

    @Select("SELECT COUNT(*) FROM t_login_history WHERE user_id = #{userId}")
    int countByUserId(Long userId);

    @Select("SELECT COUNT(*) FROM t_login_history WHERE user_id = #{userId} AND device_fingerprint = #{fingerprint}")
    int countByFingerprint(Long userId, String fingerprint);

    @Select("SELECT * FROM t_login_history WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT 1")
    LoginHistory findLatestByUserId(Long userId);
}
