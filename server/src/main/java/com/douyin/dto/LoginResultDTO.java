package com.douyin.dto;

import lombok.Data;

@Data
public class LoginResultDTO {
    private String token;
    private Long userId;
}
