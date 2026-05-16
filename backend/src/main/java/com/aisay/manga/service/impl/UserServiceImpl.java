package com.aisay.manga.service.impl;

import com.aisay.manga.dto.request.LoginRequest;
import com.aisay.manga.dto.request.RegisterRequest;
import com.aisay.manga.dto.request.UserUpdateRequest;
import com.aisay.manga.dto.response.LoginResponse;
import com.aisay.manga.dto.response.UserProfileResponse;
import com.aisay.manga.entity.User;
import com.aisay.manga.repository.UserMapper;
import com.aisay.manga.service.UserService;
import com.aisay.manga.utils.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    private final JwtUtil jwtUtil;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        ensureUsernameAvailable(request.getUsername(), null);
        ensureEmailAvailable(request.getEmail(), null);

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        userMapper.insert(user);
        return toProfileResponse(userMapper.selectById(user.getId()));
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())
                .last("LIMIT 1"));

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new LoginResponse(token, user.getId(), user.getUsername());
    }

    @Override
    public UserProfileResponse getProfile(Long userId) {
        return toProfileResponse(getUserById(userId));
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UserUpdateRequest request) {
        User user = getUserById(userId);

        if (StringUtils.isNotBlank(request.getUsername())
                && !StringUtils.equals(user.getUsername(), request.getUsername())) {
            ensureUsernameAvailable(request.getUsername(), userId);
            user.setUsername(request.getUsername());
        }

        if (StringUtils.isNotBlank(request.getEmail())
                && !StringUtils.equals(user.getEmail(), request.getEmail())) {
            ensureEmailAvailable(request.getEmail(), userId);
            user.setEmail(request.getEmail());
        }

        if (StringUtils.isNotBlank(request.getAvatarPath())) {
            user.setAvatarPath(request.getAvatarPath());
        }

        userMapper.updateById(user);
        return toProfileResponse(userMapper.selectById(userId));
    }

    private User getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new NoSuchElementException("用户不存在");
        }
        return user;
    }

    private void ensureUsernameAvailable(String username, Long currentUserId) {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(currentUserId)) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }

    private void ensureEmailAvailable(String email, Long currentUserId) {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
                .last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(currentUserId)) {
            throw new IllegalArgumentException("邮箱已存在");
        }
    }

    private UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarPath(),
                user.getCreatedAt()
        );
    }
}
