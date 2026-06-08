package com.douyin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterDTO {
    @NotBlank(message = "邮箱不能为空")
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String code;

    private String password;

    private String nickname;

    /** 角色: user=普通用户, merchant=商家 */
    private String role;

    /** 店铺名称 (商家注册时使用) */
    private String shopName;
}
