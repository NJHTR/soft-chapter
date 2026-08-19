package com.douyin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashSet;
import java.util.Set;

/** SRS HTTP API adapter; it only reads stream liveness, never media bytes. */
@Slf4j
@Component
@ConditionalOnProperty(name = "live.media.reconciliation.enabled", havingValue = "true")
public class SrsLiveProviderClient implements LiveProviderClient {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 1_000;

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String apiBase;

    public SrsLiveProviderClient(RestClient.Builder builder, ObjectMapper objectMapper,
                                 @Value("${live.media.reconciliation.srs-api-base:http://localhost:1985}") String apiBase) {
        this.client = builder.baseUrl(trimTrailingSlash(apiBase)).build();
        this.objectMapper = objectMapper;
        this.apiBase = trimTrailingSlash(apiBase);
    }

    @Override
    public LiveProviderSnapshot snapshot() {
        try {
            Set<String> result = new HashSet<>();
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
                if (parsed.streamCount() < PAGE_SIZE) {
                    return new LiveProviderSnapshot(true, result);
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
        JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
        if (!root.isObject() || root.path("code").asInt(-1) != 0) {
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
            if (active) result.add(key);
        }
        return new Page(result, streams.size());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) return null;
        return value.asText().trim();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "http://localhost:1985";
        return value.trim().replaceFirst("/+$", "");
    }

    private record Page(Set<String> activeStreams, int streamCount) {
    }
}
