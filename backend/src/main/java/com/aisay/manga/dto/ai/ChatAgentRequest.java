package com.aisay.manga.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatAgentRequest {

    private Long userId;

    private Long sessionId;

    private Long storyId;

    private String title;

    private String genre;

    private String storyStyle;

    private String synopsis;

    private String outline;

    private String userMessage;
}
