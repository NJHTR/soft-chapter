package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_active_session")
public class ActiveSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** SHA-256 of JWT token */
    private String tokenHash;

    private String deviceFingerprint;
    private String deviceName;
    private String ip;
    private String city;
    private String browserName;
    private String browserVersion;
    private String deviceOs;
    private String osVersion;
    private Integer screenWidth;
    private Integer screenHeight;
    private Integer cpuCores;
    private Integer deviceMemoryGb;
    private String gpuRenderer;

    private LocalDateTime loginTime;
    private LocalDateTime lastActiveTime;

    /** 1=active 0=revoked */
    private Integer isActive;

    private LocalDateTime createTime;
}
