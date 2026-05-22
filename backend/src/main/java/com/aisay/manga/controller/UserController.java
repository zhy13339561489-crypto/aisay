package com.aisay.manga.controller;

import com.aisay.manga.dto.request.LoginRequest;
import com.aisay.manga.dto.request.RegisterRequest;
import com.aisay.manga.dto.request.UserRoleUpdateRequest;
import com.aisay.manga.dto.request.UserUpdateRequest;
import com.aisay.manga.dto.response.ApiResponse;
import com.aisay.manga.dto.response.LoginResponse;
import com.aisay.manga.dto.response.UserManageResponse;
import com.aisay.manga.dto.response.UserProfileResponse;
import com.aisay.manga.service.UserService;
import com.aisay.manga.utils.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    /**
     * 作用：注入用户服务。
     * 调用方：Spring 容器启动时自动构造 UserController。
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 作用：注册新用户并返回用户资料。
     * 调用方：前端注册页提交后请求 POST /api/auth/register。
     */
    @PostMapping("/auth/register")
    public ApiResponse<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success("注册成功", userService.register(request));
    }

    /**
     * 作用：校验用户名密码并返回 JWT 登录令牌。
     * 调用方：前端登录页提交后请求 POST /api/auth/login。
     */
    @PostMapping("/auth/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("登录成功", userService.login(request));
    }

    /**
     * 作用：查询当前登录用户资料。
     * 调用方：前端用户资料初始化时请求 GET /api/user/profile。
     */
    @GetMapping("/user/profile")
    public ApiResponse<UserProfileResponse> getProfile() {
        return ApiResponse.success(userService.getProfile(SecurityUtils.getCurrentUserId()));
    }

    /**
     * 作用：更新当前登录用户资料。
     * 调用方：前端用户资料保存时请求 PUT /api/user/profile。
     */
    @PutMapping("/user/profile")
    public ApiResponse<UserProfileResponse> updateProfile(@Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success("更新成功", userService.updateProfile(SecurityUtils.getCurrentUserId(), request));
    }

    /**
     * 作用：root 查询用户权限列表。
     * 调用方：前端用户权限管理页面。
     */
    @GetMapping("/users")
    public ApiResponse<List<UserManageResponse>> listUsers() {
        return ApiResponse.success(userService.listUsers(SecurityUtils.getCurrentUserId()));
    }

    /**
     * 作用：root 修改其他用户权限等级。
     * 调用方：前端用户权限管理页面。
     */
    @PutMapping("/users/{id}/role")
    public ApiResponse<UserManageResponse> updateUserRole(
            @PathVariable Long id,
            @Valid @RequestBody UserRoleUpdateRequest request
    ) {
        return ApiResponse.success("权限已更新", userService.updateUserRole(SecurityUtils.getCurrentUserId(), id, request));
    }
}
