package com.aisay.manga.service.impl;

import com.aisay.manga.config.FeaturePermissionKeys;
import com.aisay.manga.config.UserRoles;
import com.aisay.manga.entity.FeaturePermission;
import com.aisay.manga.entity.User;
import com.aisay.manga.repository.FeaturePermissionMapper;
import com.aisay.manga.repository.UserMapper;
import com.aisay.manga.service.PermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PermissionServiceImpl implements PermissionService {

    private static final Set<String> ALL_ROLES = Set.of(UserRoles.ROOT, UserRoles.ADMIN, UserRoles.USER);

    private static final Set<String> ADMIN_ROOT_ROLES = Set.of(UserRoles.ROOT, UserRoles.ADMIN);

    private static final Set<String> ROOT_ONLY = Set.of(UserRoles.ROOT);

    private static final Map<String, Set<String>> DEFAULT_FEATURE_ROLES = Map.ofEntries(
            Map.entry(FeaturePermissionKeys.CHAT_USE, ALL_ROLES),
            Map.entry(FeaturePermissionKeys.STORY_LIST, ALL_ROLES),
            Map.entry(FeaturePermissionKeys.STORY_DETAIL, ALL_ROLES),
            Map.entry(FeaturePermissionKeys.STORY_GENERATE, ALL_ROLES),
            Map.entry(FeaturePermissionKeys.STORY_UPDATE_BASIC, ALL_ROLES),
            Map.entry(FeaturePermissionKeys.STORY_UPDATE_DETAIL, ALL_ROLES),
            Map.entry(FeaturePermissionKeys.STORY_REVISE_OUTLINE, ALL_ROLES),
            Map.entry(FeaturePermissionKeys.STORY_GENERATE_VOLUME_OUTLINE, ALL_ROLES),
            Map.entry(FeaturePermissionKeys.STORY_REVISE_VOLUME_OUTLINE, ALL_ROLES),
            Map.entry(FeaturePermissionKeys.STORY_UPDATE_VOLUME_OUTLINE, ALL_ROLES),
            Map.entry(FeaturePermissionKeys.STORY_GENERATE_VOLUME_SECTIONS, ALL_ROLES),
            Map.entry(FeaturePermissionKeys.STORY_GENERATE_SECTION_ASSETS, ALL_ROLES),
            Map.entry(FeaturePermissionKeys.STORY_GENERATE_SECTION_SCRIPT, ALL_ROLES),
            Map.entry(FeaturePermissionKeys.STORY_UPLOAD_ASSET_AUDIO, ALL_ROLES),
            Map.entry(FeaturePermissionKeys.STORY_DELETE, ALL_ROLES),
            Map.entry(FeaturePermissionKeys.OUTLINE_CONFIG_MANAGE, ADMIN_ROOT_ROLES),
            Map.entry(FeaturePermissionKeys.PROMPT_MANAGE, ADMIN_ROOT_ROLES),
            Map.entry(FeaturePermissionKeys.USER_MANAGE, ROOT_ONLY),
            Map.entry(FeaturePermissionKeys.FEATURE_PERMISSION_MANAGE, ROOT_ONLY)
    );

    private final UserMapper userMapper;

    private final FeaturePermissionMapper featurePermissionMapper;

    public PermissionServiceImpl(UserMapper userMapper, FeaturePermissionMapper featurePermissionMapper) {
        this.userMapper = userMapper;
        this.featurePermissionMapper = featurePermissionMapper;
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

    @Override
    public void requireFeature(Long userId, String featureKey) {
        if (!hasFeature(userId, featureKey)) {
            throw new AccessDeniedException("当前角色无权执行该功能：" + featureKey);
        }
    }

    @Override
    public boolean hasFeature(Long userId, String featureKey) {
        User user = getUser(userId);
        String role = UserRoles.normalize(user.getRole());
        if (UserRoles.ROOT.equals(role)) {
            return true;
        }
        return getAllowedRoles(featureKey).contains(role);
    }

    private User getUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new NoSuchElementException("用户不存在");
        }
        return user;
    }

    private Set<String> getAllowedRoles(String featureKey) {
        try {
            FeaturePermission permission = featurePermissionMapper.selectOne(new LambdaQueryWrapper<FeaturePermission>()
                    .eq(FeaturePermission::getFeatureKey, featureKey)
                    .last("LIMIT 1"));
            if (permission == null) {
                return DEFAULT_FEATURE_ROLES.getOrDefault(featureKey, ROOT_ONLY);
            }
            if (!Boolean.TRUE.equals(permission.getEnabled())) {
                return ROOT_ONLY;
            }
            Set<String> roles = parseRoles(permission.getAllowedRoles());
            return roles.isEmpty() ? ROOT_ONLY : roles;
        } catch (RuntimeException ex) {
            return DEFAULT_FEATURE_ROLES.getOrDefault(featureKey, ROOT_ONLY);
        }
    }

    private Set<String> parseRoles(String allowedRoles) {
        if (allowedRoles == null || allowedRoles.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(allowedRoles.split(","))
                .map(UserRoles::normalize)
                .collect(Collectors.toSet());
    }
}
