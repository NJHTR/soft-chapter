package com.douyin.rtc.webhook;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * provider webhook 账本 (rtc-persistence, migration_035)。
 * event_id 来自 LiveKit 事件,全局唯一;INSERT IGNORE + 唯一索引保证
 * 重放/乱序投递幂等,重复事件直接返回 200。
 */
@Data
@TableName("rtc_webhook_ledger")
public class RtcWebhookLedger {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** LiveKit webhook 事件 ID(幂等键) */
    private String eventId;

    /** 解析出的通话 ID,未知时为空 */
    private String callId;

    /** room_started/room_finished/participant_joined/participant_left/... */
    private String eventType;

    /** 原始负载 JSON(签名校验后的 body,留审计) */
    private String payload;

    private LocalDateTime receivedAt;

    /** 1=已消费(去重后首次处理) */
    private Integer processed;
}