package com.douyin.rtc.provider;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RTC provider 配置 (rtc-media-adapter 与 webhook 共用)。
 *
 * <p>application.yml 不写入生产敏感值,全部由环境变量注入
 * (Spring relaxed binding, kebab-case 属性 ↔ 大写下划线环境变量):
 * <ul>
 *   <li>rtc.token.ttl-seconds        ← RTC_TOKEN_TTL_SECONDS</li>
 *   <li>rtc.livekit.api-key          ← RTC_LIVEKIT_API_KEY</li>
 *   <li>rtc.livekit.api-secret       ← RTC_LIVEKIT_API_SECRET (≥32 字节,HS256 要求)</li>
   *   <li>rtc.livekit.webhook-secret   ← RTC_LIVEKIT_WEBHOOK_SECRET (与 API secret 相同)</li>
 * </ul>
 *
 * <p>api-key/api-secret 缺失时拒绝签发 token(fail-closed,不提供可用的弱默认值)。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rtc")
public class RtcProperties {

    /** token TTL(秒),默认 300;有效范围 [60, 900],超范围一律拒绝 */
    private long tokenTtlSeconds = 300L;

    /** LiveKit API key(JWT iss / kid) */
    private String livekitApiKey;

    /** LiveKit API secret,用于 HS256 签 token,生产从环境变量注入 */
    private String livekitApiSecret;

    /** LiveKit webhook 签名密钥(官方 JWT 或迁移期 HMAC),生产从环境变量注入 */
    private String livekitWebhookSecret;
}
