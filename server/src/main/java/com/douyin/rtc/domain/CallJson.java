package com.douyin.rtc.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 领域内轻量 JSON 工具,用于事件 payload 与旧消息投影 extra。
 * 使用 Jackson(项目既有依赖),不依赖 Spring 上下文。
 */
public final class CallJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CallJson() {
    }

    public static String write(Map<String, ?> value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new CallDomainException(CallErrorCode.PROVIDER_ERROR, "JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    public static Map<String, Object> read(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static int intField(Map<String, Object> map, String key, int fallback) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static long longField(Map<String, Object> map, String key, long fallback) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static String stringField(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : null;
    }
}