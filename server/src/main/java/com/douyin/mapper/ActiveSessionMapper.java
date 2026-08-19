package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.ActiveSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface ActiveSessionMapper extends BaseMapper<ActiveSession> {

    @Select("SELECT * FROM t_active_session WHERE token_hash = #{tokenHash} AND is_active = 1")
    ActiveSession findByTokenHash(@Param("tokenHash") String tokenHash);

    @Select("SELECT * FROM t_active_session WHERE user_id = #{userId} AND is_active = 1 ORDER BY last_active_time DESC")
    List<ActiveSession> findActiveByUserId(@Param("userId") Long userId);

    /** 加载所有活跃会话 (用于启动时预热缓存) */
    @Select("SELECT * FROM t_active_session WHERE is_active = 1")
    List<ActiveSession> findAllActive();

    /** 撤销单个会话 */
    @Update("UPDATE t_active_session SET is_active = 0 WHERE id = #{id}")
    int revokeById(@Param("id") Long id);

    /** 撤销某用户除当前外的所有会话 */
    @Update("UPDATE t_active_session SET is_active = 0 WHERE user_id = #{userId} AND is_active = 1 AND id != #{exceptId}")
    int revokeAllExcept(@Param("userId") Long userId, @Param("exceptId") Long exceptId);

    /** 更新最后活跃时间 */
    @Update("UPDATE t_active_session SET last_active_time = NOW() WHERE id = #{id}")
    int touchLastActive(@Param("id") Long id);

    /** 批量撤销超时僵尸会话 (lastActiveTime 超过阈值且未主动登出) */
    @Update("UPDATE t_active_session SET is_active = 0 WHERE is_active = 1 AND last_active_time < #{threshold}")
    int revokeStale(@Param("threshold") LocalDateTime threshold);
}
