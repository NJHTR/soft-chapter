package com.douyin.vo;

import com.douyin.entity.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 前端兼容格式的用户信息
 * 对应前端 store/pinia.ts 中 userinfo 的结构
 */
@Data
public class UserVO {

    private Long uid;

    @JsonProperty("unique_id")
    private String uniqueId;

    @JsonProperty("short_id")
    private String shortId;

    private String nickname;
    private String signature;
    private Integer gender;
    private String birthday;
    private String province;
    private String city;

    @JsonProperty("avatar_168x168")
    private Avatar avatar168;

    @JsonProperty("avatar_300x300")
    private Avatar avatar300;

    @JsonProperty("cover_url")
    private List<Avatar> coverUrl;

    @JsonProperty("white_cover_url")
    private List<Avatar> whiteCoverUrl;

    @JsonProperty("follower_count")
    private Long followerCount;

    @JsonProperty("following_count")
    private Long followingCount;

    @JsonProperty("total_favorited")
    private Long totalFavorited;

    @JsonProperty("video_count")
    private Integer videoCount;

    @JsonProperty("user_age")
    private Integer userAge;

    @JsonProperty("is_followed")
    private Boolean isFollowed;

    @Data
    public static class Avatar {
        @JsonProperty("url_list")
        private List<String> urlList;

        public static Avatar of(String url) {
            Avatar a = new Avatar();
            a.urlList = url != null && !url.isEmpty() ? List.of(url) : List.of();
            return a;
        }
    }

    private static final String DEFAULT_AVATAR = "/images/default-avatar.svg";
    private static final String DEFAULT_COVER = "/images/default-cover.svg";

    public static UserVO from(User user) {
        if (user == null) return null;
        UserVO vo = new UserVO();
        vo.uid = user.getUid();
        vo.uniqueId = user.getUniqueId();
        vo.shortId = user.getUniqueId(); // short_id 同 unique_id
        vo.nickname = user.getNickname();
        vo.signature = user.getSignature();
        vo.gender = user.getGender();
        vo.birthday = user.getBirthday();
        vo.province = user.getProvince();
        vo.city = user.getCity();
        String avatar168 = user.getAvatar168Url();
        String avatar300 = user.getAvatar300Url();
        String cover = user.getCoverUrl();
        if (avatar168 == null || avatar168.isEmpty()) avatar168 = DEFAULT_AVATAR;
        if (avatar300 == null || avatar300.isEmpty()) avatar300 = DEFAULT_AVATAR;
        if (cover == null || cover.isEmpty()) cover = DEFAULT_COVER;
        vo.avatar168 = Avatar.of(avatar168);
        vo.avatar300 = Avatar.of(avatar300);
        vo.coverUrl = List.of(Avatar.of(cover));
        vo.whiteCoverUrl = List.of(Avatar.of(cover));
        vo.followerCount = user.getFollowerCount();
        vo.followingCount = user.getFollowingCount();
        vo.totalFavorited = user.getTotalFavorited();
        vo.videoCount = user.getVideoCount();
        vo.userAge = calcAge(user.getBirthday());
        return vo;
    }

    private static Integer calcAge(String birthday) {
        if (birthday == null || birthday.isEmpty()) return -1;
        try {
            java.time.LocalDate birth = java.time.LocalDate.parse(birthday);
            java.time.LocalDate now = java.time.LocalDate.now();
            int age = now.getYear() - birth.getYear();
            if (now.getDayOfYear() < birth.getDayOfYear()) age--;
            return Math.max(age, 0);
        } catch (Exception e) {
            return -1;
        }
    }
}
