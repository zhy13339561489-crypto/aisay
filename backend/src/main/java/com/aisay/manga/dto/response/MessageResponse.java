package com.aisay.manga.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    private Long id;

    private Long sessionId;

    private String role;

    private String content;

    private LocalDateTime createdAt;
}
