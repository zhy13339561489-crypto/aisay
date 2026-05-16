package com.aisay.manga.service;

import com.aisay.manga.dto.request.LoginRequest;
import com.aisay.manga.dto.request.RegisterRequest;
import com.aisay.manga.dto.request.UserUpdateRequest;
import com.aisay.manga.dto.response.LoginResponse;
import com.aisay.manga.dto.response.UserProfileResponse;

public interface UserService {

    UserProfileResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    UserProfileResponse getProfile(Long userId);

    UserProfileResponse updateProfile(Long userId, UserUpdateRequest request);
}
