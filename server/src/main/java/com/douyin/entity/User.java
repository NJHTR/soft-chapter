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

    private String phone;

    /** 邮箱 */
    private String email;

    @JsonIgnore
    private String password;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
