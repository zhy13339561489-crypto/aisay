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

    /**
     * 作用：注入用户数据访问、密码编码和 JWT 工具。
     * 调用方：Spring 容器启动时自动构造 UserServiceImpl。
     */
    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 作用：注册用户，校验用户名和邮箱唯一性后写入用户表。
     * 调用方：UserController#register。
     */
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

    /**
     * 作用：校验用户名密码，成功后签发 JWT。
     * 调用方：UserController#login。
     */
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

    /**
     * 作用：查询用户资料并转换为响应 DTO。
     * 调用方：UserController#getProfile。
     */
    @Override
    public UserProfileResponse getProfile(Long userId) {
        return toProfileResponse(getUserById(userId));
    }

    /**
     * 作用：更新用户名、邮箱和头像路径，并校验唯一性。
     * 调用方：UserController#updateProfile。
     */
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

    /**
     * 作用：按用户 ID 查询用户，不存在时抛出 404 语义异常。
     * 调用方：getProfile、updateProfile。
     */
    private User getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new NoSuchElementException("用户不存在");
        }
        return user;
    }

    /**
     * 作用：校验用户名是否可用，更新自己资料时允许当前用户保留原用户名。
     * 调用方：register、updateProfile。
     */
    private void ensureUsernameAvailable(String username, Long currentUserId) {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(currentUserId)) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }

    /**
     * 作用：校验邮箱是否可用，更新自己资料时允许当前用户保留原邮箱。
     * 调用方：register、updateProfile。
     */
    private void ensureEmailAvailable(String email, Long currentUserId) {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
                .last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(currentUserId)) {
            throw new IllegalArgumentException("邮箱已存在");
        }
    }

    /**
     * 作用：将 User 实体转换为用户资料响应 DTO。
     * 调用方：register、getProfile、updateProfile。
     */
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
