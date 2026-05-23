package com.aisay.manga.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeaturePermissionResponse {

    private Long id;

    private String featureKey;

    private String featureName;

    private String category;

    private String description;

    private List<String> allowedRoles;

    private Boolean enabled;

    private Integer sortOrder;
}
