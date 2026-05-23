package com.aisay.manga.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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

    private String userRole;

    private List<ChatMemoryMessage> recentMessages;

    private String longTermMemory;

    private Map<String, Object> keyFacts;

    private List<ChatMemoryMessage> summaryCandidateMessages;

    private Integer summaryCandidateCount;
}
