package com.aisay.manga.utils;

import com.aisay.manga.dto.ai.ChatAgentRequest;
import com.aisay.manga.dto.ai.ChatAgentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Component
public class AiEngineClient {

    private final RestClient restClient;

    private final String chatAgentPath;

    /**
     * 作用：创建访问 Python FastAPI 对话 Agent 的 HTTP 客户端。
     * 调用方：Spring 容器启动时自动实例化，ChatServiceImpl 注入使用。
     */
    public AiEngineClient(
            RestClient.Builder restClientBuilder,
            @Value("${ai.engine.base-url:http://localhost:5000}") String baseUrl,
            @Value("${ai.engine.chat-agent-path:/api/chat/agent}") String chatAgentPath
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ZERO);

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.chatAgentPath = chatAgentPath;
    }

    /**
     * 作用：同步调用 Python /api/chat/agent，让大模型完成问题重写、路由分发并返回 Java 方法名和参数。
     * 调用方：ChatServiceImpl#runAgentAndDispatch。
     */
    public ChatAgentResponse runChatAgent(ChatAgentRequest request) {
        try {
            ChatAgentResponse response = restClient.post()
                    .uri(chatAgentPath)
                    .body(request)
                    .retrieve()
                    .body(ChatAgentResponse.class);

            if (response == null) {
                throw new IllegalStateException("Python chat agent returned empty response");
            }
            return response;
        } catch (RestClientException ex) {
            throw new IllegalStateException("Python chat agent API is unavailable: " + chatAgentPath, ex);
        }
    }
}
