package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_login_history")
public class LoginHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String email;
    private String loginMethod;
    private String loginStatus;

    private String ip;
    private String country;
    private String region;
    private String city;
    private String isp;

    private String deviceFingerprint;
    private String userAgentRaw;

    private String deviceOs;
    private String osVersion;

    private String browserName;
    private String browserVersion;
    private String browserLanguage;

    private Integer screenWidth;
    private Integer screenHeight;
    private Integer colorDepth;
    private Double pixelRatio;

    private Integer cpuCores;
    private Integer deviceMemoryGb;
    private String gpuRenderer;

    private String connectionType;
    private String platformType;
    private Integer touchSupport;

    private LocalDateTime createTime;
}
