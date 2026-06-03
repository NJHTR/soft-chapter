package com.douyin.controller;

import com.douyin.common.Result;
import com.douyin.dto.LoginDTO;
import com.douyin.dto.LoginResultDTO;
import com.douyin.dto.RegisterDTO;
import com.douyin.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public Result<LoginResultDTO> login(@Valid @RequestBody LoginDTO dto) {
        try {
            String token = userService.login(dto.getEmail(), dto.getPassword());
            var user = userService.getByEmail(dto.getEmail());
            var result = new LoginResultDTO();
            result.setToken(token);
            result.setUserId(user.getUid());
            return Result.ok(result);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterDTO dto) {
        try {
            userService.register(dto.getEmail(), dto.getPassword(), dto.getNickname());
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }
}
