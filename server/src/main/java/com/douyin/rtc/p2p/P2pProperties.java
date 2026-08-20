package com.douyin.rtc.p2p;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * RTC-015 受控 P2P 配置。默认 fail-closed:enabled=false。
 * signaling-secret 为空时每实例随机生成(单实例部署语义;多实例须显式注入共享密钥)。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rtc.p2p")
public class P2pProperties {

    /** RTC_P2P_ENABLED 默认 false(fail-closed) */
    private boolean enabled = false;

    /** 探测预算(毫秒),契约要求 1500~3000 */
    private long probeBudgetMs = 2000;

    /** 双方 consent 有效期(TTL) */
    private long consentTtlSeconds = 60;

    /** 单条信令 envelope 最大字节数(offer/answer SDP) */
    private int signalingMaxBytes = 32 * 1024;

    /** 单条 ICE envelope 最大候选数 */
    private int maxIceCandidates = 10;

    /** 每用户每秒信令速率上限 */
    private int signalRatePerSecond = 10;

    /** 邮箱(收件箱)缓冲区条数上限 */
    private int mailboxMaxEntries = 32;

    /** 邮箱条目 TTL */
    private long mailboxTtlSeconds = 30;

    /** envelope HMAC 密钥;空则每实例随机(fail-closed:跨实例不可互信) */
    private String signalingSecret = "";

    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);
}