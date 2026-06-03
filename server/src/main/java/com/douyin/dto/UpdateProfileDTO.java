package com.douyin.dto;

import lombok.Data;

@Data
public class UpdateProfileDTO {
    private String nickname;
    private String signature;
    private Integer gender;
    private String birthday;
    private String province;
    private String city;
}
