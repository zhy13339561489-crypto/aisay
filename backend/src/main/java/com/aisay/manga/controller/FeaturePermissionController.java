package com.aisay.manga.controller;

import com.aisay.manga.dto.request.FeaturePermissionRequest;
import com.aisay.manga.dto.response.ApiResponse;
import com.aisay.manga.dto.response.FeaturePermissionResponse;
import com.aisay.manga.service.FeaturePermissionService;
import com.aisay.manga.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/feature-permissions")
@RequiredArgsConstructor
public class FeaturePermissionController {

    private final FeaturePermissionService featurePermissionService;

    @GetMapping
    public ApiResponse<List<FeaturePermissionResponse>> listPermissions() {
        return ApiResponse.success(featurePermissionService.listPermissions(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<FeaturePermissionResponse> updatePermission(
            @PathVariable Long id,
            @Valid @RequestBody FeaturePermissionRequest request
    ) {
        return ApiResponse.success("功能权限已更新", featurePermissionService.updatePermission(SecurityUtils.getCurrentUserId(), id, request));
    }

    @GetMapping("/me")
    public ApiResponse<List<String>> listCurrentUserAllowedFeatures() {
        return ApiResponse.success(featurePermissionService.listCurrentUserAllowedFeatures(SecurityUtils.getCurrentUserId()));
    }
}
