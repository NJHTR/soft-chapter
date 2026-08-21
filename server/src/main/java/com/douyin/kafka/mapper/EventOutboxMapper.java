package com.douyin.kafka.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.kafka.entity.EventOutbox;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * outbox Mapper。claim/mark 采用 CAS（status 守卫），
 * 保证多实例调度器同一行只有一个实例投递。
 */
@Mapper
public interface EventOutboxMapper extends BaseMapper<EventOutbox> {

    String COLUMNS = "id,topic,event_key,event_id,payload,status,retry_count,next_attempt_at,"
            + "created_at,updated_at,sent_at";

    @Select("SELECT " + COLUMNS + " FROM event_outbox WHERE status = 'PENDING' AND next_attempt_at <= #{now} "
            + "ORDER BY id ASC LIMIT #{limit}")
    List<EventOutbox> findPendingBatch(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** CAS 认领：只有 PENDING 行能被抢占，返回 0 表示已被其他实例认领。 */
    @Update("UPDATE event_outbox SET status = 'PROCESSING', updated_at = NOW() "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int tryClaim(@Param("id") Long id);

    @Update("UPDATE event_outbox SET status = 'SENT', sent_at = NOW(), updated_at = NOW() WHERE id = #{id}")
    int markSent(@Param("id") Long id);

    @Update("UPDATE event_outbox SET status = 'PENDING', retry_count = retry_count + 1, "
            + "next_attempt_at = #{nextAttemptAt}, updated_at = NOW() WHERE id = #{id}")
    int scheduleRetry(@Param("id") Long id, @Param("nextAttemptAt") LocalDateTime nextAttemptAt);

    @Update("UPDATE event_outbox SET status = 'DEAD', updated_at = NOW() WHERE id = #{id}")
    int markDead(@Param("id") Long id);

    /** 恢复：PROCESSING 卡死（实例崩溃/主从切换）超时的行回到 PENDING 重新投递。 */
    @Update("UPDATE event_outbox SET status = 'PENDING', next_attempt_at = #{recoverAt}, "
            + "updated_at = NOW() WHERE status = 'PROCESSING' AND updated_at < #{staleBefore}")
    int recoverStale(@Param("staleBefore") LocalDateTime staleBefore, @Param("recoverAt") LocalDateTime recoverAt);

    @Select("SELECT COUNT(*) FROM event_outbox WHERE status = 'PENDING'")
    long countPending();

    /** 清理已终态（SENT/DEAD）的历史行，防止无界增长。 */
    @Delete("DELETE FROM event_outbox WHERE status IN ('SENT','DEAD') AND updated_at < #{olderThan} LIMIT 500")
    int purgeTerminal(@Param("olderThan") LocalDateTime olderThan);
}
