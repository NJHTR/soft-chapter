package com.douyin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Public SRS endpoints. Empty bases deliberately produce same-origin paths. */
@Component
public class LiveMediaProperties {

    private final String rtcBase;
    private final String httpBase;
    private final String rtmpBase;

    public LiveMediaProperties(
            @Value("${live.media.srs-rtc-base:}") String rtcBase,
            @Value("${live.media.srs-http-base:}") String httpBase,
            @Value("${live.media.rtmp-base:rtmp://localhost:1935/live/}") String rtmpBase) {
        this.rtcBase = trimTrailingSlash(rtcBase);
        this.httpBase = trimTrailingSlash(httpBase);
        this.rtmpBase = ensureTrailingSlash(rtmpBase);
    }

    public String whip(String streamKey) {
        return whip(streamKey, null);
    }

    public String whip(String streamKey, String token) {
        return endpoint(rtcBase, "/rtc/v1/whip/?app=live&stream=" + encode(streamKey) + tokenQuery(token, true), "/media/srs");
    }

    public String whep(String streamKey) {
        return whep(streamKey, null);
    }

    public String whep(String streamKey, String token) {
        return endpoint(rtcBase, "/rtc/v1/whep/?app=live&stream=" + encode(streamKey) + tokenQuery(token, true), "/media/srs");
    }

    public String hls(String streamKey) {
        return hls(streamKey, null);
    }

    public String hls(String streamKey, String token) {
        return endpoint(httpBase, "/live/" + encode(streamKey) + ".m3u8" + tokenQuery(token, false), "/media/srs-http");
    }

    public String httpFlv(String streamKey) {
        return httpFlv(streamKey, null);
    }

    public String httpFlv(String streamKey, String token) {
        return endpoint(httpBase, "/live/" + encode(streamKey) + ".flv" + tokenQuery(token, false), "/media/srs-http");
    }

    public String rtmp(String streamKey) {
        return rtmp(streamKey, null);
    }

    public String rtmp(String streamKey, String token) {
        return rtmpBase + streamKey + tokenQuery(token, rtmpBase.contains("?"));
    }

    private static String endpoint(String base, String path, String relativeBase) {
        return base.isBlank() ? relativeBase + path : base + path;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) return "";
        return value.trim().replaceFirst("/+$", "");
    }

    private static String ensureTrailingSlash(String value) {
        String v = value == null ? "" : value.trim();
        return v.endsWith("/") ? v : v + "/";
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String tokenQuery(String token, boolean queryAlreadyPresent) {
        if (token == null || token.isBlank()) return "";
        return (queryAlreadyPresent ? "&" : "?") + "token=" + encode(token);
    }
}
