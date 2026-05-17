package com.aisay.manga.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryOutlineReviseRequest {

    @NotBlank(message = "修改意见不能为空")
    @Size(max = 5000, message = "修改意见长度不能超过5000个字符")
    private String suggestion;
}
