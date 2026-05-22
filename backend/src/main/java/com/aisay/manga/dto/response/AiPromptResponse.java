package com.aisay.manga.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiPromptResponse {

    private Long id;

    private String promptKey;

    private String basePromptKey;

    private String promptScope;

    private String matchGenre;

    private String matchStyle;

    private Integer priority;

    private String promptName;

    private String category;

    private String description;

    private String templateContent;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<AiPromptParameterResponse> parameters = new ArrayList<>();
}
