package com.douyin.controller;

import com.douyin.common.Result;
import com.douyin.dto.LoginDTO;
import com.douyin.dto.LoginResultDTO;
import com.douyin.dto.RegisterDTO;
import com.douyin.service.EmailService;
import com.douyin.service.LoginDeviceService;
import com.douyin.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserService userService;
    private final EmailService emailService;
    private final LoginDeviceService loginDeviceService;

    public AuthController(UserService userService, EmailService emailService,
                          LoginDeviceService loginDeviceService) {
        this.userService = userService;
        this.emailService = emailService;
        this.loginDeviceService = loginDeviceService;
    }

    @PostMapping("/login")
    public Result<LoginResultDTO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest req) {
        try {
            String token = userService.login(dto.getEmail(), dto.getPassword());
            var user = userService.getByEmail(dto.getEmail());
            var result = new LoginResultDTO();
            result.setToken(token);
            result.setUserId(user.getUid());
            result.setRole(user.getRole() != null ? user.getRole() : "user");

            // 记录登录设备 + 发送系统通知
            loginDeviceService.recordAndNotify(user.getUid(), user.getUniqueId(),
                    dto.getEmail(), "password", req);

            return Result.ok(result);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterDTO dto) {
        try {
            if (!emailService.verify(dto.getEmail(), dto.getCode())) {
                return Result.fail("验证码错误或已过期");
            }
            userService.register(dto.getEmail(), dto.getPassword(), dto.getNickname(), dto.getRole(), dto.getShopName());
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }
}
