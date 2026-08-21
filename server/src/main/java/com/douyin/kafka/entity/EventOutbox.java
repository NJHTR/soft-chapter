package com.douyin.kafka.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Kafka 事务 Outbox 行（chat-persistence 模块的 outbox 归属）。
 * 业务事件先落 outbox（PENDING），由 EventOutboxDispatcher 幂等投递到 Kafka；
 * 投递成功 SENT，超过最大尝试次数 DEAD 进入人工/告警恢复。
 * 只存放控制事件 JSON，禁止媒体字节（宪法 §2.1）。
 */
@Data
@TableName("event_outbox")
public class EventOutbox {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_DEAD = "DEAD";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String topic;

    private String eventKey;

    private String eventId;

    private String payload;

    private String status;

    private Integer retryCount;

    private LocalDateTime nextAttemptAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    private LocalDateTime sentAt;
}
