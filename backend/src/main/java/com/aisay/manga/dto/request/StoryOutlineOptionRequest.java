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
public class StoryOutlineOptionRequest {

    @NotBlank(message = "配置类型不能为空")
    @Pattern(regexp = "GENRE|STYLE", message = "配置类型只能是 GENRE 或 STYLE")
    private String type;

    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称长度不能超过 100 个字符")
    private String name;

    @Size(max = 500, message = "说明长度不能超过 500 个字符")
    private String description;

    private Integer sortOrder;

    private Boolean enabled;
}
