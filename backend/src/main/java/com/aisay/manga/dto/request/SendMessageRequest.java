package com.aisay.manga.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 10000, message = "消息内容不能超过10000个字符")
    private String content;
}
