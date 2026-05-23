package com.aisay.manga.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMemoryMessage {

    private String role;

    private String content;

    private String createdAt;
}
