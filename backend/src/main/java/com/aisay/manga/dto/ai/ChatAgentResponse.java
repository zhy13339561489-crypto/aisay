package com.aisay.manga.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatAgentResponse {

    private String rewrittenQuestion;

    private String route;

    private String javaMethod;

    private Map<String, Object> javaMethodArgs;

    private String assistantMessage;
}
