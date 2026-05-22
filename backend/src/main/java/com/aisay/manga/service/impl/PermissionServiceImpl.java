package com.aisay.manga.service.impl;

import com.aisay.manga.config.UserRoles;
import com.aisay.manga.entity.User;
import com.aisay.manga.repository.UserMapper;
import com.aisay.manga.service.PermissionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class PermissionServiceImpl implements PermissionService {

    private final UserMapper userMapper;

    public PermissionServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void requireRoot(Long userId) {
        User user = getUser(userId);
        if (!UserRoles.isRoot(user.getRole())) {
            throw new AccessDeniedException("仅 root 可以执行该操作");
        }
    }

    @Override
    public void requireAdminOrRoot(Long userId) {
        User user = getUser(userId);
        if (!UserRoles.isAdminOrRoot(user.getRole())) {
            throw new AccessDeniedException("需要 admin 或 root 权限");
        }
    }

    private User getUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new NoSuchElementException("用户不存在");
        }
        return user;
    }
}
