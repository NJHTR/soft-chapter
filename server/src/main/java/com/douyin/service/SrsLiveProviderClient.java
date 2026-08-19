package com.douyin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** SRS HTTP API adapter; it only reads stream liveness, never media bytes. */
@Slf4j
@Component
@ConditionalOnProperty(name = "live.media.reconciliation.enabled", havingValue = "true")
public class SrsLiveProviderClient implements LiveProviderClient {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 1_000;
    private static final long DEFAULT_CONNECT_TIMEOUT_MILLIS = 2_000;
    private static final long DEFAULT_READ_TIMEOUT_MILLIS = 5_000;
    private static final long MIN_TIMEOUT_MILLIS = 250;
    private static final long MAX_TIMEOUT_MILLIS = 30_000;

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String apiBase;

    @Autowired
    public SrsLiveProviderClient(RestClient.Builder builder, ObjectMapper objectMapper,
                                 @Value("${live.media.reconciliation.srs-api-base:http://localhost:1985}") String apiBase,
                                 @Value("${live.media.reconciliation.connect-timeout-ms:2000}") long connectTimeoutMillis,
                                 @Value("${live.media.reconciliation.read-timeout-ms:5000}") long readTimeoutMillis) {
        this(builder, objectMapper, apiBase, timeoutConfiguration(connectTimeoutMillis, readTimeoutMillis));
    }

    /** Compatibility constructor for mock-backed unit tests. */
    SrsLiveProviderClient(RestClient.Builder builder, ObjectMapper objectMapper, String apiBase) {
        this.client = builder.baseUrl(trimTrailingSlash(apiBase)).build();
        this.objectMapper = objectMapper;
        this.apiBase = trimTrailingSlash(apiBase);
    }

    SrsLiveProviderClient(RestClient.Builder builder, ObjectMapper objectMapper, String apiBase,
                          TimeoutConfiguration timeouts) {
        this.client = timedClient(builder, apiBase, timeouts);
        this.objectMapper = objectMapper;
        this.apiBase = trimTrailingSlash(apiBase);
    }

    static TimeoutConfiguration timeoutConfiguration(long connectTimeoutMillis, long readTimeoutMillis) {
        return new TimeoutConfiguration(
                boundedTimeout(connectTimeoutMillis, DEFAULT_CONNECT_TIMEOUT_MILLIS),
                boundedTimeout(readTimeoutMillis, DEFAULT_READ_TIMEOUT_MILLIS));
    }

    static RestClient timedClient(RestClient.Builder builder, String apiBase, TimeoutConfiguration timeouts) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeouts.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeouts.readTimeout());
        return builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(trimTrailingSlash(apiBase))
                .build();
    }

    private static Duration boundedTimeout(long configuredMillis, long defaultMillis) {
        long effectiveMillis = configuredMillis <= 0 ? defaultMillis : configuredMillis;
        return Duration.ofMillis(Math.max(MIN_TIMEOUT_MILLIS, Math.min(MAX_TIMEOUT_MILLIS, effectiveMillis)));
    }

    @Override
    public LiveProviderSnapshot snapshot() {
        try {
            Set<String> result = new HashSet<>();
            Map<String, String> sessions = new HashMap<>();
            String providerServerId = null;
            for (int page = 0; page < MAX_PAGES; page++) {
                int start = page * PAGE_SIZE;
                String body = client.get()
                        .uri(uri -> uri.path("/api/v1/streams")
                                .queryParam("start", start)
                                .queryParam("count", PAGE_SIZE)
                                .build())
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(String.class);
                Page parsed = parsePage(body);
                result.addAll(parsed.activeStreams());
                sessions.putAll(parsed.activeSessions());
                if (providerServerId == null) {
                    providerServerId = parsed.providerServerId();
                } else if (!providerServerId.equals(parsed.providerServerId())) {
                    throw new IllegalStateException("SRS server generation changed during snapshot");
                }
                if (parsed.streamCount() < PAGE_SIZE) {
                    return new LiveProviderSnapshot(true, result, sessions, providerServerId);
                }
            }
            log.warn("SRS reconciliation stream listing exceeded {} pages", MAX_PAGES);
            return LiveProviderSnapshot.unavailable();
        } catch (Exception ex) {
            log.warn("SRS reconciliation API unavailable at {}: {}", apiBase, ex.getMessage());
            return LiveProviderSnapshot.unavailable();
        }
    }

    private Page parsePage(String body) throws Exception {
        Set<String> result = new HashSet<>();
        Map<String, String> sessions = new HashMap<>();
        JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
        if (!root.isObject() || root.path("code").asInt(-1) != 0
                || text(root, "server") == null) {
            throw new IllegalStateException("invalid SRS streams response");
        }
        JsonNode streams = root.path("streams");
        if (!streams.isArray()) throw new IllegalStateException("missing SRS streams array");
        for (JsonNode stream : streams) {
            String key = text(stream, "name");
            if (key == null) key = text(stream, "stream");
            if (key == null) continue;
            JsonNode publish = stream.path("publish");
            boolean active = publish.isBoolean() && publish.asBoolean()
                    || publish.isObject() && publish.path("active").isBoolean()
                    && publish.path("active").asBoolean();
            if (active) {
                result.add(key);
                String cid = scalarText(publish, "cid");
                if (cid == null) throw new IllegalStateException("missing SRS publish client generation");
                sessions.put(key, text(root, "server") + ":" + cid);
            }
        }
        return new Page(result, sessions, streams.size(), text(root, "server"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) return null;
        return value.asText().trim();
    }

    private static String scalarText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode() || value.asText().isBlank()) return null;
        return value.asText().trim();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "http://localhost:1985";
        return value.trim().replaceFirst("/+$", "");
    }

    private record Page(Set<String> activeStreams,
                        Map<String, String> activeSessions,
                         int streamCount,
                         String providerServerId) {
    }

    record TimeoutConfiguration(Duration connectTimeout, Duration readTimeout) {
    }
}
