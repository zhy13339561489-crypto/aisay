package com.aisay.manga.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatStartRequest {

    @Size(max = 200, message = "会话标题长度不能超过200个字符")
    private String title;
}
