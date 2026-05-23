package com.aisay.manga.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatAgentResponse {

    private String rewrittenQuestion;

    private String resolvedQuestion;

    private String route;

    private String module;

    private String intent;

    private String requiredPermission;

    private Map<String, Object> importantInfo;

    private List<String> missingInfo;

    private String javaMethod;

    private Map<String, Object> javaMethodArgs;

    private String assistantMessage;

    private String longTermMemory;

    private Map<String, Object> keyFacts;

    private Integer summarizedMessageCount;
}
