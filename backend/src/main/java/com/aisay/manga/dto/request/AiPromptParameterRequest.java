package com.aisay.manga.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiPromptParameterRequest {

    @NotBlank(message = "参数方向不能为空")
    @Pattern(regexp = "INPUT|OUTPUT", message = "参数方向只能是 INPUT 或 OUTPUT")
    private String direction;

    @NotBlank(message = "参数标识不能为空")
    @Size(max = 100, message = "参数标识长度不能超过 100 个字符")
    private String paramKey;

    @NotBlank(message = "参数名称不能为空")
    @Size(max = 100, message = "参数名称长度不能超过 100 个字符")
    private String paramName;

    @NotBlank(message = "参数类型不能为空")
    @Size(max = 50, message = "参数类型长度不能超过 50 个字符")
    private String dataType;

    private Boolean requiredFlag;

    @Size(max = 1000, message = "参数说明长度不能超过 1000 个字符")
    private String description;

    @Size(max = 1000, message = "参数示例长度不能超过 1000 个字符")
    private String exampleValue;

    private Integer sortOrder;
}
