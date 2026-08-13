package com.douyin.rtc.webhook;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * provider webhook 账本 Mapper (rtc-persistence)。
 * INSERT IGNORE + event_id 唯一索引: 重复返回 0 行,调用方按重放幂等处理。
 */
@Mapper
public interface RtcWebhookLedgerMapper extends BaseMapper<RtcWebhookLedger> {

    @Insert("INSERT IGNORE INTO rtc_webhook_ledger "
            + "(id, event_id, call_id, event_type, payload, received_at, processed) "
            + "VALUES (#{id}, #{eventId}, #{callId}, #{eventType}, #{payload}, #{receivedAt}, #{processed})")
    int insertIgnore(RtcWebhookLedger ledger);
}