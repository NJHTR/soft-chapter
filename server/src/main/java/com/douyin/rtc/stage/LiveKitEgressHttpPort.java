package com.douyin.rtc.stage;

import com.douyin.rtc.provider.RtcProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stage -&gt; SRS 的 LiveKit Egress 控制面端口(默认关闭)。
 *
 * <p>走 LiveKit Egress twirp REST(StartRoomCompositeEgress),Bearer 使用与
 * {@code LiveKitTokenService} 相同的手写 HS256 AccessToken(admin/roomAdmin grant,
 * 30 秒 TTL);媒体字节不经过 Spring/Kafka/聊天 WS。
 * provider 不可用或 HTTP 失败抛 {@link StageProviderException};调用方按
 * generation-aware reconciler 重试,不把观众迁入 LiveKit。
 */
@Slf4j
@Service
public class LiveKitEgressHttpPort implements StageProviderPort {

    private static final String TWIRP_PATH = "/twirp/livekit.Egress/StartRoomCompositeEgress";

    private final StageProperties stageProperties;
    private final RtcProperties rtcProperties;
    private final RestTemplate restTemplate;

    public LiveKitEgressHttpPort(StageProperties stageProperties, RtcProperties rtcProperties) {
        this.stageProperties = stageProperties;
        this.rtcProperties = rtcProperties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(stageProperties.getEgress().getTimeoutMs());
        factory.setReadTimeout(stageProperties.getEgress().getTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    /** 包内可见,仅供同包测试注入 MockRestServiceServer 绑定的实例。 */
    LiveKitEgressHttpPort(StageProperties stageProperties, RtcProperties rtcProperties, RestTemplate restTemplate) {
        this.stageProperties = stageProperties;
        this.rtcProperties = rtcProperties;
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean revokePublishPermission(StageMember member) {
        // LiveKit 1.5+ 的 participant permission 移除需要房间 admin 调用
        // rpc UpdateParticipant(permission.canPublish=false) 或强制 DisconnectParticipant;
        // 该调用与 Egress 同走 REST 控制面。当前仓库无 LiveKit REST client:
        // 返回 false 使成员保持 providerPending,由 reconciler 重试并告警(契约 §6 不提前标记已撤)。
        log.warn("[STAGE] revokePublishPermission 未实现,成员 {} 保持 pending(不可提前标记为已撤销)", member.key());
        return false;
    }

    @Override
    public String requestStageEgress(String liveId, String roomName) {
        if (!stageProperties.getEgress().isEnabled()) {
            throw new StageProviderException("Egress 未启用(rtc.stage.egress.enabled=false)");
        }
        Map<String, Object> outputFile = new LinkedHashMap<>();
        outputFile.put("filepath", String.format(stageProperties.getEgress().getFilePathTemplate(),
                Long.parseLong(liveId), System.currentTimeMillis()));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("file", outputFile);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roomName", roomName);
        body.put("output", output);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken(roomName));
        String url = stageProperties.getEgress().getBaseUrl() + TWIRP_PATH;
        try {
            String json = restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
            return parseEgressId(json, url);
        } catch (StageProviderException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw new StageProviderException("Egress 调用失败: " + url + " http=" + e.getRawStatusCode(), e);
        } catch (Exception e) {
            throw new StageProviderException("Egress 调用失败: " + url + " (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    /** 从 twirp JSON 响应解析 egressId,失败视为 provider 错误(501 语义)。 */
    private String parseEgressId(String json, String url) {
        if (json == null) {
            throw new StageProviderException("Egress 空响应: " + url);
        }
        int idx = json.indexOf("\"egressId\"");
        if (idx < 0) {
            throw new StageProviderException("Egress 响应缺少 egressId: " + url);
        }
        int start = json.indexOf('"', idx + 10) + 1;
        int end = json.indexOf('"', start);
        if (start <= 0 || end < 0) {
            throw new StageProviderException("Egress 响应 egressId 无法解析: " + url);
        }
        return json.substring(start, end);
    }

    /** 与 LiveKitTokenService 相同结构的 HS256 AccessToken,admin/roomAdmin grant,30s TTL。 */
    private String adminToken(String roomName) {
        String apiKey = rtcProperties.getLivekitApiKey();
        String apiSecret = rtcProperties.getLivekitApiSecret();
        SecretKey key = Keys.hmacShaKeyFor(apiSecret.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> video = new LinkedHashMap<>();
        video.put("room", roomName);
        video.put("roomJoin", true);
        video.put("canPublish", true);
        video.put("canSubscribe", true);
        video.put("canPublishData", true);
        video.put("admin", true);
        video.put("roomAdmin", true);
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .issuer(apiKey)
                .subject("egress:" + roomName)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date(now))
                .notBefore(new Date(now))
                .expiration(new Date(now + 30_000))
                .claim("video", video)
                .header()
                .keyId(apiKey)
                .and()
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}