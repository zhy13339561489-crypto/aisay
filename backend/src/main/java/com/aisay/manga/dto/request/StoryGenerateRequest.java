package com.aisay.manga.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryGenerateRequest {

    @NotNull(message = "会话ID不能为空")
    private Long sessionId;
}
