package com.aisay.manga.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryUpdateRequest {

    @Size(max = 200, message = "标题长度不能超过200个字符")
    private String title;

    @Size(max = 100, message = "类型长度不能超过100个字符")
    private String genre;

    @Size(max = 100, message = "风格长度不能超过100个字符")
    private String style;

    @Size(max = 5000, message = "摘要长度不能超过5000个字符")
    private String synopsis;
}
