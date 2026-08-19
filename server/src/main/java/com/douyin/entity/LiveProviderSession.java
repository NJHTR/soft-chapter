package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Durable SRS provider session projection. It contains control-plane
 * identifiers only; media bytes, credentials and complete URLs never belong
 * in this table.
 */
@Data
@TableName("live_provider_session")
public class LiveProviderSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;
    private String provider;
    private String direction; // PUBLISH / PLAY
    private String clientId;
    private String serverId;
    /** Stable provider-supplied generation: server_id + ':' + client_id. */
    private String providerSessionId;
    private String streamKey;
    private Long userId;
    private String state; // ACTIVE / ENDED
    private String lastEvent;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime endedAt;
}
