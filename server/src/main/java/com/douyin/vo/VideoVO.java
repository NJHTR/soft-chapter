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

    public Long getAwemeId() {
        return awemeId;
    }

    public void setAwemeId(Long awemeId) {
        this.awemeId = awemeId;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public VideoInfo getVideo() {
        return video;
    }

    public void setVideo(VideoInfo video) {
        this.video = video;
    }

    public Statistics getStatistics() {
        return statistics;
    }

    public void setStatistics(Statistics statistics) {
        this.statistics = statistics;
    }

    public UserVO getAuthor() {
        return author;
    }

    public void setAuthor(UserVO author) {
        this.author = author;
    }

    public Music getMusic() {
        return music;
    }

    public void setMusic(Music music) {
        this.music = music;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public Boolean getLoved() {
        return isLoved;
    }

    public void setLoved(Boolean loved) {
        isLoved = loved;
    }

    public Boolean getAttention() {
        return isAttention;
    }

    public void setAttention(Boolean attention) {
        isAttention = attention;
    }

    public Boolean getCollect() {
        return isCollect;
    }

    public void setCollect(Boolean collect) {
        isCollect = collect;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    private String desc;

    /** 内容类型: recommend-video, long-video, image, text */
    private String type;

    /** 时长(毫秒), 前端用 duration 字段 */
    private Long duration;

    private VideoInfo video;

    @JsonProperty("statistics")
    private Statistics statistics;

    private UserVO author;

    private Music music;

    private String city;
    private String address;

    /** 审核状态: pending/approved/rejected */
    private String status;

    /** 多图URL列表 (用于图文轮播帖子) */
    @JsonProperty("image_urls")
    private List<String> imageUrls;

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

        public UrlList getPlayAddr() {
            return playAddr;
        }

        public void setPlayAddr(UrlList playAddr) {
            this.playAddr = playAddr;
        }

        public UrlList getCover() {
            return cover;
        }

        public void setCover(UrlList cover) {
            this.cover = cover;
        }

        public String getPoster() {
            return poster;
        }

        public void setPoster(String poster) {
            this.poster = poster;
        }
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

        public Long getDiggCount() {
            return diggCount;
        }

        public void setDiggCount(Long diggCount) {
            this.diggCount = diggCount;
        }

        public Long getCommentCount() {
            return commentCount;
        }

        public void setCommentCount(Long commentCount) {
            this.commentCount = commentCount;
        }

        public Long getShareCount() {
            return shareCount;
        }

        public void setShareCount(Long shareCount) {
            this.shareCount = shareCount;
        }

        public Long getCollectCount() {
            return collectCount;
        }

        public void setCollectCount(Long collectCount) {
            this.collectCount = collectCount;
        }

        public Long getPlayCount() {
            return playCount;
        }

        public void setPlayCount(Long playCount) {
            this.playCount = playCount;
        }

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

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getCover() {
            return cover;
        }

        public void setCover(String cover) {
            this.cover = cover;
        }

        public UrlList getPlayUrl() {
            return playUrl;
        }

        public void setPlayUrl(UrlList playUrl) {
            this.playUrl = playUrl;
        }

        @JsonProperty("play_url")
        private UrlList playUrl;
    }

    public static VideoVO from(Video v, UserVO author, boolean isLoved, boolean isAttention, boolean isCollect) {
        VideoVO vo = new VideoVO();
        vo.awemeId = v.getId();
        vo.desc = v.getDesc();
        vo.type = v.getType();
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

        // 多图URL列表
        if (v.getImageUrls() != null && !v.getImageUrls().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                vo.imageUrls = mapper.readValue(v.getImageUrls(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            } catch (Exception ignored) {
                vo.imageUrls = List.of();
            }
        } else {
            vo.imageUrls = List.of();
        }

        vo.city = "";
        vo.address = "";
        vo.status = v.getStatus();
        vo.isLoved = isLoved;
        vo.isAttention = isAttention;
        vo.isCollect = isCollect;

        return vo;
    }
}
