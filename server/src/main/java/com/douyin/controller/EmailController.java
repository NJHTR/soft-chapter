package com.douyin.controller;

import com.douyin.common.Result;
import com.douyin.dto.LoginResultDTO;
import com.douyin.entity.User;
import com.douyin.service.EmailService;
import com.douyin.service.LoginDeviceService;
import com.douyin.service.UserService;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/email")
public class EmailController {

    private final EmailService emailService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final LoginDeviceService loginDeviceService;

    public EmailController(EmailService emailService, UserService userService, JwtUtil jwtUtil,
                           LoginDeviceService loginDeviceService) {
        this.emailService = emailService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.loginDeviceService = loginDeviceService;
    }

    /** 发送邮箱验证码 */
    @PostMapping("/send-code")
    public Result<?> sendCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || !email.contains("@")) {
            return Result.fail("请输入有效邮箱");
        }
        try {
            emailService.sendCode(email);
            return Result.ok();
        } catch (Exception e) {
            return Result.fail("发送失败: " + e.getMessage());
        }
    }

    /** 邮箱验证码登录, 未注册则自动注册 */
    @PostMapping("/login")
    public Result<LoginResultDTO> login(@RequestBody Map<String, String> body, HttpServletRequest req) {
        String email = body.get("email");
        String code = body.get("code");
        if (email == null || code == null) {
            return Result.fail("参数不完整");
        }
        if (!emailService.verify(email, code)) {
            return Result.fail("验证码错误或已过期");
        }

        // 查找或自动注册
        User user = userService.getByEmail(email);
        if (user == null) {
            user = userService.registerByEmail(email);
        }

        String token = jwtUtil.generateToken(user.getUid());
        var result = new LoginResultDTO();
        result.setToken(token);
        result.setUserId(user.getUid());

        // 记录登录设备 + 发送系统通知
        loginDeviceService.recordAndNotify(user.getUid(), user.getUniqueId(),
                email, "code", req);

        return Result.ok(result);
    }
}
