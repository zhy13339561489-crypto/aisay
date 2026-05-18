package com.aisay.manga.utils;

import com.aisay.manga.dto.ai.ChatAgentRequest;
import com.aisay.manga.dto.ai.ChatAgentResponse;
import com.aisay.manga.dto.ai.StoryOutlineGenerateRequest;
import com.aisay.manga.dto.ai.StoryOutlineGenerateResponse;
import com.aisay.manga.dto.ai.StoryOutlineReviseRequest;
import com.aisay.manga.dto.ai.StoryOutlineReviseResponse;
import com.aisay.manga.dto.ai.StoryVolumeOutlineGenerateRequest;
import com.aisay.manga.dto.ai.StoryVolumeOutlineGenerateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Component
public class AiEngineClient {

    private final RestClient restClient;

    private final String storyOutlinePath;

    private final String storyOutlineRevisePath;

    private final String storyVolumeOutlinePath;

    private final String chatAgentPath;

    public AiEngineClient(
            RestClient.Builder restClientBuilder,
            @Value("${ai.engine.base-url:http://localhost:5000}") String baseUrl,
            @Value("${ai.engine.story-outline-path:/api/story/outline}") String storyOutlinePath,
            @Value("${ai.engine.story-outline-revise-path:/api/story/outline/revise}") String storyOutlineRevisePath,
            @Value("${ai.engine.story-volume-outline-path:/api/story/volume-outline}") String storyVolumeOutlinePath,
            @Value("${ai.engine.chat-agent-path:/api/chat/agent}") String chatAgentPath
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ZERO);

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.storyOutlinePath = storyOutlinePath;
        this.storyOutlineRevisePath = storyOutlineRevisePath;
        this.storyVolumeOutlinePath = storyVolumeOutlinePath;
        this.chatAgentPath = chatAgentPath;
    }

    public StoryOutlineGenerateResponse generateStoryOutline(StoryOutlineGenerateRequest request) {
        try {
            StoryOutlineGenerateResponse response = restClient.post()
                    .uri(storyOutlinePath)
                    .body(request)
                    .retrieve()
                    .body(StoryOutlineGenerateResponse.class);

            if (response == null) {
                throw new IllegalStateException("Python AI 引擎返回为空");
            }
            return response;
        } catch (RestClientException ex) {
            throw new IllegalStateException("Python 剧情大纲接口暂不可用，请确认 FastAPI 服务已启动并实现 " + storyOutlinePath, ex);
        }
    }

    public StoryOutlineReviseResponse reviseStoryOutline(StoryOutlineReviseRequest request) {
        try {
            StoryOutlineReviseResponse response = restClient.post()
                    .uri(storyOutlineRevisePath)
                    .body(request)
                    .retrieve()
                    .body(StoryOutlineReviseResponse.class);

            if (response == null) {
                throw new IllegalStateException("Python AI 引擎返回为空");
            }
            return response;
        } catch (RestClientException ex) {
            throw new IllegalStateException("Python 大纲修改接口暂不可用，请确认 FastAPI 服务已启动并实现 " + storyOutlineRevisePath, ex);
        }
    }

    public StoryVolumeOutlineGenerateResponse generateVolumeOutline(StoryVolumeOutlineGenerateRequest request) {
        try {
            StoryVolumeOutlineGenerateResponse response = restClient.post()
                    .uri(storyVolumeOutlinePath)
                    .body(request)
                    .retrieve()
                    .body(StoryVolumeOutlineGenerateResponse.class);

            if (response == null) {
                throw new IllegalStateException("Python AI engine returned empty response");
            }
            return response;
        } catch (RestClientException ex) {
            throw new IllegalStateException("Python volume outline API is unavailable: " + storyVolumeOutlinePath, ex);
        }
    }

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
