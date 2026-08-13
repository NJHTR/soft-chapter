package com.douyin.rtc.provider;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.domain.CallParticipant;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.domain.CallState;
import com.douyin.rtc.domain.CallStateMachine;
import com.douyin.rtc.service.CallService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * LiveKit AccessToken 签发 (rtc-media-adapter)。
 *
 * <p>约定(CALL_DOMAIN_MODEL.md §2 转移表):
 * <ul>
 *   <li>ACCEPTED 前(RINGING/CREATED)不签发媒体 token——首批 token 只由
 *       negotiate.start(ACCEPTED→NEGOTIATING)授予,守卫「provider token 有效」;</li>
 *   <li>ACCEPTED 时先经 {@link CallService#startNegotiation} 推进后再签发;</li>
 *   <li>NEGOTIATING/CONNECTED 直接补签(重连/换设备场景,幂等安全);</li>
 *   <li>发布/订阅权限由服务端决定,不信任客户端 roster:
 *       发起者恒可发布;成员在通话已被接受(ACCEPTED 之后)可发布;订阅一律可。</li>
 * </ul>
 *
 * <p>token 按 LiveKit AccessToken 标准结构手写 JWT(HS256,与 JwtUtil 同库 jjwt,
 * 不引入 LiveKit Java SDK):header {alg,typ,kid=apiKey},
 * claims {iss=apiKey, sub=userId, jti, iat, nbf, exp, video:{room, roomJoin,
 * canPublish, canSubscribe, canPublishData}}。token 不写入日志(宪法 §2.7)。
 */
@Slf4j
@Service
public class LiveKitTokenService {

    public static final int MIN_TTL_SECONDS = 60;
    public static final int MAX_TTL_SECONDS = 900;

    private static final String SYSTEM_EVENT_PREFIX = "sys:token:";

    private final RtcProperties properties;
    private final CallService callService;

    public LiveKitTokenService(RtcProperties properties, CallService callService) {
        this.properties = properties;
        this.callService = callService;
    }

    /**
     * 签发 token。ttlSeconds 为空时使用配置默认值。
     */
    public TokenResult issue(String callId, Long actorId, Integer requestedTtlSeconds, String traceId) {
        if (callId == null || callId.isBlank()) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "缺少 call_id");
        }
        if (actorId == null) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED, "未登录,无权获取媒体 token");
        }
        CallSession session = callService.getCall(callId);
        if (session == null) {
            throw new CallDomainException(CallErrorCode.SESSION_NOT_FOUND, "通话不存在: " + callId);
        }
        CallState state = CallState.valueOf(session.getState());
        if (CallStateMachine.isFinal(state)) {
            throw new CallDomainException(CallErrorCode.INVALID_STATE_TRANSITION, "通话已结束,不能签发媒体 token");
        }
        if (state == CallState.CREATED || state == CallState.RINGING) {
            // 宪法 CALL_DOMAIN_MODEL §2: ACCEPTED→negotiate.start 才授予 token
            throw new CallDomainException(CallErrorCode.INVALID_STATE_TRANSITION,
                    "通话尚未接听,不能签发媒体 token");
        }
        CallParticipant participant = callService.getParticipant(callId, actorId);
        if (participant == null) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED, "不是通话参与者,无权获取媒体 token");
        }
        if (isExpired(session)) {
            throw new CallDomainException(CallErrorCode.CALL_EXPIRED, "协商窗口已过期,请重新发起通话");
        }
        long ttl = effectiveTtlSeconds(requestedTtlSeconds);
        if (ttl < MIN_TTL_SECONDS || ttl > MAX_TTL_SECONDS) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT,
                    "token TTL 必须在 60-900 秒之间,实际 " + ttl);
        }
        String apiKey = properties.getLivekitApiKey();
        String apiSecret = properties.getLivekitApiSecret();
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            throw new CallDomainException(CallErrorCode.PROVIDER_ERROR,
                    "LiveKit api-key/api-secret 未配置(环境变量 RTC_LIVEKIT_API_KEY / RTC_LIVEKIT_API_SECRET)");
        }

        // ACCEPTED → NEGOTIATING(守卫: provider token 有效)。幂等/重放安全,失败不影响本次签发。
        if (state == CallState.ACCEPTED) {
            callService.startNegotiation(callId, actorId, SYSTEM_EVENT_PREFIX + UUID.randomUUID(), traceId);
        }

        String roomName = roomNameOf(session);
        boolean canPublish = canPublish(participant, state);
        String token = sign(apiKey, apiSecret, actorId, roomName, canPublish, ttl);

        log.info("[RTC-TOKEN] issued: callId={} userId={} room={} ttl={}s state={}",
                callId, actorId, roomName, ttl, state.name());
        return new TokenResult(token, roomName, callId, actorId, ttl, epochSeconds(System.currentTimeMillis() + ttl * 1000));
    }

    /**
     * 房间名服务端决定,禁止客户端传入:room_id 非空则用之,
     * 为空时确定性派生 callId(不做随机混淆,后续任务再处理)。
     */
    public String roomNameOf(CallSession session) {
        if (session.getRoomId() != null && !session.getRoomId().isBlank()) {
            return session.getRoomId();
        }
        return session.getCallId();
    }

    private boolean canPublish(CallParticipant participant, CallState state) {
        // 发起者恒可发布;已 ACCEPTED 的成员(通话整体被接受)可发布。
        return "initiator".equals(participant.getRole())
                || state == CallState.ACCEPTED
                || state == CallState.NEGOTIATING
                || state == CallState.CONNECTED;
    }

    private long effectiveTtlSeconds(Integer requested) {
        return requested != null && requested > 0 ? requested : properties.getTokenTtlSeconds();
    }

    private boolean isExpired(CallSession session) {
        return session.getExpiresAt() != null && !session.getExpiresAt().isAfter(LocalDateTime.now());
    }

    /** 构造 LiveKit AccessToken 标准 GRANT 结构(JWS HS256)。 */
    private String sign(String apiKey, String apiSecret, Long userId, String roomName,
                        boolean canPublish, long ttl) {
        SecretKey key = Keys.hmacShaKeyFor(apiSecret.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> video = new LinkedHashMap<>();
        video.put("room", roomName);
        video.put("roomJoin", true);
        video.put("canPublish", canPublish);
        video.put("canSubscribe", true);
        video.put("canPublishData", true);
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .issuer(apiKey)
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date(now))
                .notBefore(new Date(now))
                .expiration(new Date(now + ttl * 1000))
                .claim("video", video)
                .header()
                .keyId(apiKey)
                .and()
                .signWith(key)
                .compact();
    }

    private static long epochSeconds(long millis) {
        return millis / 1000;
    }
}