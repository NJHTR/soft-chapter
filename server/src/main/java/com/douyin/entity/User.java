package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Long uid;

    private String uniqueId;
    private String nickname;
    private String signature;
    private Integer gender;
    private String birthday;
    private String province;
    private String city;

    /** 头像 168x168 */
    @TableField("avatar_168_url")
    private String avatar168Url;

    /** 头像 300x300 */
    @TableField("avatar_300_url")
    private String avatar300Url;

    /** 个人主页背景图 */
    private String coverUrl;

    private Long followerCount;
    private Long followingCount;
    private Long totalFavorited;
    private Integer videoCount;

    /** 主页访客展示开关: 0关闭 1开启 */
    private Integer visitorDisplay;

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getAvatar168Url() {
        return avatar168Url;
    }

    public void setAvatar168Url(String avatar168Url) {
        this.avatar168Url = avatar168Url;
    }

    public String getAvatar300Url() {
        return avatar300Url;
    }

    public void setAvatar300Url(String avatar300Url) {
        this.avatar300Url = avatar300Url;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public Long getFollowerCount() {
        return followerCount;
    }

    public void setFollowerCount(Long followerCount) {
        this.followerCount = followerCount;
    }

    public Long getFollowingCount() {
        return followingCount;
    }

    public void setFollowingCount(Long followingCount) {
        this.followingCount = followingCount;
    }

    public Long getTotalFavorited() {
        return totalFavorited;
    }

    public void setTotalFavorited(Long totalFavorited) {
        this.totalFavorited = totalFavorited;
    }

    public Integer getVideoCount() {
        return videoCount;
    }

    public void setVideoCount(Integer videoCount) {
        this.videoCount = videoCount;
    }

    public Integer getVisitorDisplay() {
        return visitorDisplay;
    }

    public void setVisitorDisplay(Integer visitorDisplay) {
        this.visitorDisplay = visitorDisplay;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getIsDelete() {
        return isDelete;
    }

    public void setIsDelete(Integer isDelete) {
        this.isDelete = isDelete;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    private String phone;

    /** 邮箱 */
    private String email;

    /** 角色: user=普通用户, merchant=SeekFlow商家 */
    private String role;

    @JsonIgnore
    private String password;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
