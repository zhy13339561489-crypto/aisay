package com.aisay.manga.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryGenerateRequest {

    @NotBlank(message = "漫剧题材不能为空")
    @Size(max = 100, message = "题材长度不能超过100个字符")
    private String genre;

    @Size(max = 5000, message = "大致剧情长度不能超过5000个字符")
    private String plot;
}
