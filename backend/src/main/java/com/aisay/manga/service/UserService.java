package com.aisay.manga.service;

import com.aisay.manga.dto.request.LoginRequest;
import com.aisay.manga.dto.request.RegisterRequest;
import com.aisay.manga.dto.request.UserUpdateRequest;
import com.aisay.manga.dto.response.LoginResponse;
import com.aisay.manga.dto.response.UserProfileResponse;

public interface UserService {

    /**
     * 作用：注册新用户。
     * 调用方：UserController#register。
     */
    UserProfileResponse register(RegisterRequest request);

    /**
     * 作用：登录并生成 JWT。
     * 调用方：UserController#login。
     */
    LoginResponse login(LoginRequest request);

    /**
     * 作用：查询用户资料。
     * 调用方：UserController#getProfile。
     */
    UserProfileResponse getProfile(Long userId);

    /**
     * 作用：更新用户资料。
     * 调用方：UserController#updateProfile。
     */
    UserProfileResponse updateProfile(Long userId, UserUpdateRequest request);
}
