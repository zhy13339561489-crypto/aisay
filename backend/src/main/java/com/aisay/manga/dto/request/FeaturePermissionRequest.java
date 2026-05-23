package com.aisay.manga.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class FeaturePermissionRequest {

    @NotEmpty(message = "Allowed roles must not be empty")
    private List<String> allowedRoles;

    private Boolean enabled;
}
