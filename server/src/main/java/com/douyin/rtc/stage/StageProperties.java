package com.douyin.rtc.stage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 配置(rtc.stage.*)。
 * <ul>
 *   <li>cap-publishers:首期硬上限(默认 8,扩到 16 需容量/首帧/CPU/egress 证据);</li>
 *   <li>host-user-ids:主持人 userId 列表,为空时批准/撤销类命令一律拒绝(NOT_AUTHORIZED);</li>
 *   <li>egress.*:Stage -&gt; SRS 的 LiveKit Egress 端口,默认关闭。</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "rtc.stage")
public class StageProperties {

    /** 首期发布者硬上限(契约 §6)。 */
    private int capPublishers = 8;

    private List<Long> hostUserIds = new ArrayList<>();

    private Egress egress = new Egress();

    public int getCapPublishers() {
        return capPublishers;
    }

    public void setCapPublishers(int capPublishers) {
        this.capPublishers = capPublishers;
    }

    public List<Long> getHostUserIds() {
        return hostUserIds;
    }

    public void setHostUserIds(List<Long> hostUserIds) {
        this.hostUserIds = hostUserIds;
    }

    public Egress getEgress() {
        return egress;
    }

    public void setEgress(Egress egress) {
        this.egress = egress;
    }

    public static class Egress {
        /** 默认关闭;开启后 Stage -&gt; SRS 才允许触发 Egress 命令。 */
        private boolean enabled = false;
        /** LiveKit Egress twirp 基础 URL,如 http://egress:7885 */
        private String baseUrl = "http://stage-egress:7885";
        /** HTTP 连接/读取超时(ms) */
        private int timeoutMs = 5000;
        /** 导出文件模板(output.file.filepath),如 /out/stage-{liveId}-{timestamp}.mp4 */
        private String filePathTemplate = "/out/stage-%d-%d.mp4";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public String getFilePathTemplate() {
            return filePathTemplate;
        }

        public void setFilePathTemplate(String filePathTemplate) {
            this.filePathTemplate = filePathTemplate;
        }
    }
}