package com.aisay.manga.service;

import com.aisay.manga.dto.request.FeaturePermissionRequest;
import com.aisay.manga.dto.response.FeaturePermissionResponse;

import java.util.List;

public interface FeaturePermissionService {

    List<FeaturePermissionResponse> listPermissions(Long operatorUserId);

    FeaturePermissionResponse updatePermission(Long operatorUserId, Long id, FeaturePermissionRequest request);

    FeaturePermissionResponse updatePermissionByFeatureKey(Long operatorUserId, String featureKey, FeaturePermissionRequest request);

    List<String> listCurrentUserAllowedFeatures(Long userId);
}
