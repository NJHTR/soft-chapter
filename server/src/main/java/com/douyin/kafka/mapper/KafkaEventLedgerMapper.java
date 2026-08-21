package com.douyin.kafka.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * Kafka 消费幂等账本 Mapper。
 * 主键 (topic, event_id)，INSERT IGNORE 原子去重（MySQL 专用语义，
 * 本项目唯一数据库为 MySQL）。账本按 processed_at 定期清理。
 */
@Mapper
public interface KafkaEventLedgerMapper {

    @Select("SELECT COUNT(*) FROM kafka_event_ledger WHERE topic = #{topic} AND event_id = #{eventId}")
    int exists(@Param("topic") String topic, @Param("eventId") String eventId);

    /** @return 0 表示该事件已处理过（去重命中），1 表示本次记账成功 */
    @Insert("INSERT IGNORE INTO kafka_event_ledger (topic, event_id) VALUES (#{topic}, #{eventId})")
    int markProcessed(@Param("topic") String topic, @Param("eventId") String eventId);

    @Delete("DELETE FROM kafka_event_ledger WHERE processed_at < #{olderThan}")
    int purgeOlderThan(@Param("olderThan") LocalDateTime olderThan);
}
