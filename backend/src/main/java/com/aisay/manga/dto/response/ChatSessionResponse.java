package com.aisay.manga.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionResponse {

    private Long id;

    private Long storyId;

    private String sessionKey;

    private String title;

    private String status;

    private LocalDateTime startedAt;

    private LocalDateTime lastActive;
}
