package com.douyin.vo;

import com.douyin.entity.Video;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 前端兼容格式的视频信息 — 匹配 BaseVideo.vue / ItemToolbar.vue 的字段结构
 */
@Data
public class VideoVO {

    @JsonProperty("aweme_id")
    private Long awemeId;

    private String desc;

    /** 时长(毫秒), 前端用 duration 字段 */
    private Long duration;

    private VideoInfo video;

    @JsonProperty("statistics")
    private Statistics statistics;

    private UserVO author;

    private Music music;

    private String city;
    private String address;

    @JsonProperty("is_loved")
    private Boolean isLoved;

    @JsonProperty("is_attention")
    private Boolean isAttention;

    @JsonProperty("is_collect")
    private Boolean isCollect;

    @Data
    public static class VideoInfo {
        @JsonProperty("play_addr")
        private UrlList playAddr;

        private UrlList cover;

        private String poster;
    }

    @Data
    public static class UrlList {
        @JsonProperty("url_list")
        private List<String> urlList;

        public static UrlList of(String url) {
            UrlList u = new UrlList();
            u.urlList = url != null && !url.isEmpty() ? List.of(url) : List.of();
            return u;
        }
    }

    @Data
    public static class Statistics {
        @JsonProperty("digg_count")
        private Long diggCount;

        @JsonProperty("comment_count")
        private Long commentCount;

        @JsonProperty("share_count")
        private Long shareCount;

        @JsonProperty("collect_count")
        private Long collectCount;

        @JsonProperty("play_count")
        private Long playCount;
    }

    @Data
    public static class Music {
        private String title;
        private String cover;

        @JsonProperty("play_url")
        private UrlList playUrl;
    }

    public static VideoVO from(Video v, UserVO author, boolean isLoved, boolean isAttention, boolean isCollect) {
        VideoVO vo = new VideoVO();
        vo.awemeId = v.getId();
        vo.desc = v.getDesc();
        vo.duration = (long) (v.getDuration() * 1000); // 秒 → 毫秒

        VideoInfo info = new VideoInfo();
        info.playAddr = UrlList.of(v.getVideoUrl());
        // 封面: 优先用 cover_url, 没有就用视频地址做兜底
        String cover = v.getCoverUrl() != null && !v.getCoverUrl().isEmpty()
                ? v.getCoverUrl() : v.getVideoUrl();
        info.cover = UrlList.of(cover);
        info.poster = cover;
        vo.video = info;

        Statistics stats = new Statistics();
        stats.diggCount = v.getLikeCount() != null ? v.getLikeCount() : 0L;
        stats.commentCount = v.getCommentCount() != null ? v.getCommentCount() : 0L;
        stats.shareCount = v.getShareCount() != null ? v.getShareCount() : 0L;
        stats.collectCount = v.getCollectCount() != null ? v.getCollectCount() : 0L;
        stats.playCount = v.getPlayCount() != null ? v.getPlayCount() : 0L;
        vo.statistics = stats;

        vo.author = author;

        Music music = new Music();
        music.title = v.getMusicTitle() != null ? v.getMusicTitle() : "原创";
        vo.music = music;

        vo.city = "";
        vo.address = "";
        vo.isLoved = isLoved;
        vo.isAttention = isAttention;
        vo.isCollect = isCollect;

        return vo;
    }
}
