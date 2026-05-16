package com.aisay.manga.controller;

import com.aisay.manga.dto.request.LoginRequest;
import com.aisay.manga.dto.request.RegisterRequest;
import com.aisay.manga.dto.request.UserUpdateRequest;
import com.aisay.manga.dto.response.ApiResponse;
import com.aisay.manga.dto.response.LoginResponse;
import com.aisay.manga.dto.response.UserProfileResponse;
import com.aisay.manga.service.UserService;
import com.aisay.manga.utils.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/auth/register")
    public ApiResponse<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success("注册成功", userService.register(request));
    }

    @PostMapping("/auth/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("登录成功", userService.login(request));
    }

    @GetMapping("/user/profile")
    public ApiResponse<UserProfileResponse> getProfile() {
        return ApiResponse.success(userService.getProfile(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/user/profile")
    public ApiResponse<UserProfileResponse> updateProfile(@Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success("更新成功", userService.updateProfile(SecurityUtils.getCurrentUserId(), request));
    }
}
