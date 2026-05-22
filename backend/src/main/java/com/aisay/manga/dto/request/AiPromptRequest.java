package com.aisay.manga.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiPromptRequest {

    @NotBlank(message = "Prompt Key 不能为空")
    @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "Prompt Key 只能包含字母、数字、下划线、点和短横线")
    @Size(max = 100, message = "Prompt Key 长度不能超过 100 个字符")
    private String promptKey;

    @Pattern(regexp = "DEFAULT|SPECIFIC", message = "Prompt 作用域只能是 DEFAULT 或 SPECIFIC")
    private String promptScope;

    @Pattern(regexp = "[A-Za-z0-9_.-]*", message = "基础 Prompt Key 只能包含字母、数字、下划线、点和短横线")
    @Size(max = 100, message = "基础 Prompt Key 长度不能超过 100 个字符")
    private String basePromptKey;

    @Size(max = 100, message = "匹配题材长度不能超过 100 个字符")
    private String matchGenre;

    @Size(max = 100, message = "匹配风格长度不能超过 100 个字符")
    private String matchStyle;

    private Integer priority;

    @NotBlank(message = "Prompt 名称不能为空")
    @Size(max = 100, message = "Prompt 名称长度不能超过 100 个字符")
    private String promptName;

    @Size(max = 50, message = "分类长度不能超过 50 个字符")
    private String category;

    @Size(max = 1000, message = "说明长度不能超过 1000 个字符")
    private String description;

    @NotBlank(message = "Prompt 模板内容不能为空")
    private String templateContent;

    private Boolean enabled;

    @Valid
    private List<AiPromptParameterRequest> parameters = new ArrayList<>();
}
