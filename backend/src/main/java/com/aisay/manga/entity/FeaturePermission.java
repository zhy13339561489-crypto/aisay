package com.aisay.manga.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("feature_permissions")
public class FeaturePermission {

    private Long id;

    private String featureKey;

    private String featureName;

    private String category;

    private String description;

    private String allowedRoles;

    private Boolean enabled;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
