package com.aisay.manga.service.impl;

import com.aisay.manga.config.FeaturePermissionKeys;
import com.aisay.manga.config.UserRoles;
import com.aisay.manga.dto.request.FeaturePermissionRequest;
import com.aisay.manga.dto.response.FeaturePermissionResponse;
import com.aisay.manga.entity.FeaturePermission;
import com.aisay.manga.entity.User;
import com.aisay.manga.repository.FeaturePermissionMapper;
import com.aisay.manga.repository.UserMapper;
import com.aisay.manga.service.FeaturePermissionService;
import com.aisay.manga.service.PermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class FeaturePermissionServiceImpl implements FeaturePermissionService {

    private static final List<String> STORY_FEATURES = List.of(
            FeaturePermissionKeys.CHAT_USE,
            FeaturePermissionKeys.STORY_LIST,
            FeaturePermissionKeys.STORY_DETAIL,
            FeaturePermissionKeys.STORY_GENERATE,
            FeaturePermissionKeys.STORY_UPDATE_BASIC,
            FeaturePermissionKeys.STORY_UPDATE_DETAIL,
            FeaturePermissionKeys.STORY_REVISE_OUTLINE,
            FeaturePermissionKeys.STORY_GENERATE_VOLUME_OUTLINE,
            FeaturePermissionKeys.STORY_REVISE_VOLUME_OUTLINE,
            FeaturePermissionKeys.STORY_UPDATE_VOLUME_OUTLINE,
            FeaturePermissionKeys.STORY_GENERATE_VOLUME_SECTIONS,
            FeaturePermissionKeys.STORY_GENERATE_SECTION_ASSETS,
            FeaturePermissionKeys.STORY_GENERATE_SECTION_SCRIPT,
            FeaturePermissionKeys.STORY_UPLOAD_ASSET_AUDIO,
            FeaturePermissionKeys.STORY_DELETE
    );

    private static final Map<String, List<String>> DEFAULT_ALLOWED_FEATURES = Map.of(
            UserRoles.ROOT,
            java.util.stream.Stream.concat(
                    STORY_FEATURES.stream(),
                    java.util.stream.Stream.of(
                            FeaturePermissionKeys.OUTLINE_CONFIG_MANAGE,
                            FeaturePermissionKeys.PROMPT_MANAGE,
                            FeaturePermissionKeys.USER_MANAGE,
                            FeaturePermissionKeys.FEATURE_PERMISSION_MANAGE
                    )
            ).toList(),
            UserRoles.ADMIN,
            java.util.stream.Stream.concat(
                    STORY_FEATURES.stream(),
                    java.util.stream.Stream.of(
                            FeaturePermissionKeys.OUTLINE_CONFIG_MANAGE,
                            FeaturePermissionKeys.PROMPT_MANAGE
                    )
            ).toList(),
            UserRoles.USER,
            STORY_FEATURES
    );

    private final FeaturePermissionMapper featurePermissionMapper;

    private final PermissionService permissionService;

    private final UserMapper userMapper;

    public FeaturePermissionServiceImpl(
            FeaturePermissionMapper featurePermissionMapper,
            PermissionService permissionService,
            UserMapper userMapper
    ) {
        this.featurePermissionMapper = featurePermissionMapper;
        this.permissionService = permissionService;
        this.userMapper = userMapper;
    }

    @Override
    public List<FeaturePermissionResponse> listPermissions(Long operatorUserId) {
        permissionService.requireRoot(operatorUserId);
        return selectAllPermissions().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public FeaturePermissionResponse updatePermission(Long operatorUserId, Long id, FeaturePermissionRequest request) {
        permissionService.requireRoot(operatorUserId);
        FeaturePermission permission = featurePermissionMapper.selectById(id);
        if (permission == null) {
            throw new NoSuchElementException("功能权限不存在");
        }
        permission.setAllowedRoles(serializeRoles(request.getAllowedRoles()));
        if (request.getEnabled() != null) {
            permission.setEnabled(request.getEnabled());
        }
        featurePermissionMapper.updateById(permission);
        return toResponse(featurePermissionMapper.selectById(id));
    }

    @Override
    @Transactional
    public FeaturePermissionResponse updatePermissionByFeatureKey(Long operatorUserId, String featureKey, FeaturePermissionRequest request) {
        permissionService.requireRoot(operatorUserId);
        String normalizedFeatureKey = featureKey == null ? "" : featureKey.trim();
        if (normalizedFeatureKey.isEmpty()) {
            throw new IllegalArgumentException("缺少参数：featureKey");
        }
        FeaturePermission permission = featurePermissionMapper.selectOne(new LambdaQueryWrapper<FeaturePermission>()
                .eq(FeaturePermission::getFeatureKey, normalizedFeatureKey)
                .last("LIMIT 1"));
        if (permission == null) {
            throw new NoSuchElementException("功能权限不存在：" + normalizedFeatureKey);
        }
        return updatePermission(operatorUserId, permission.getId(), request);
    }

    @Override
    public List<String> listCurrentUserAllowedFeatures(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new NoSuchElementException("用户不存在");
        }
        String role = UserRoles.normalize(user.getRole());
        try {
            return selectAllPermissions().stream()
                    .filter(permission -> UserRoles.ROOT.equals(role) || Boolean.TRUE.equals(permission.getEnabled()))
                    .filter(permission -> UserRoles.ROOT.equals(role) || parseRoles(permission.getAllowedRoles()).contains(role))
                    .map(FeaturePermission::getFeatureKey)
                    .toList();
        } catch (RuntimeException ex) {
            return DEFAULT_ALLOWED_FEATURES.getOrDefault(role, List.of());
        }
    }

    private List<FeaturePermission> selectAllPermissions() {
        return featurePermissionMapper.selectList(new LambdaQueryWrapper<FeaturePermission>()
                .orderByAsc(FeaturePermission::getSortOrder)
                .orderByAsc(FeaturePermission::getId));
    }

    private FeaturePermissionResponse toResponse(FeaturePermission permission) {
        return new FeaturePermissionResponse(
                permission.getId(),
                permission.getFeatureKey(),
                permission.getFeatureName(),
                permission.getCategory(),
                permission.getDescription(),
                parseRoles(permission.getAllowedRoles()),
                permission.getEnabled(),
                permission.getSortOrder()
        );
    }

    private String serializeRoles(List<String> roles) {
        List<String> safeRoles = roles == null ? List.of() : roles;
        return java.util.stream.Stream.concat(safeRoles.stream(), java.util.stream.Stream.of(UserRoles.ROOT))
                .map(UserRoles::normalize)
                .distinct()
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElseThrow(() -> new IllegalArgumentException("Allowed roles must not be empty"));
    }

    private List<String> parseRoles(String allowedRoles) {
        if (allowedRoles == null || allowedRoles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedRoles.split(","))
                .map(UserRoles::normalize)
                .distinct()
                .toList();
    }
}
