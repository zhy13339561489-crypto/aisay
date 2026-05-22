package com.aisay.manga.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiPromptParameterResponse {

    private Long id;

    private String direction;

    private String paramKey;

    private String paramName;

    private String dataType;

    private Boolean requiredFlag;

    private String description;

    private String exampleValue;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
