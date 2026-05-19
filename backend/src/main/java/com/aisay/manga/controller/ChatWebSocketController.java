package com.aisay.manga.controller;

import com.aisay.manga.dto.request.SendMessageRequest;
import com.aisay.manga.dto.response.ApiResponse;
import com.aisay.manga.dto.response.MessageResponse;
import com.aisay.manga.service.ChatService;
import com.aisay.manga.utils.JwtUtil;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

@Controller
@Validated
public class ChatWebSocketController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ChatService chatService;

    private final JwtUtil jwtUtil;

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 作用：注入聊天服务、JWT 工具和 WebSocket 消息模板。
     * 调用方：Spring 容器启动时自动构造 ChatWebSocketController。
     */
    public ChatWebSocketController(ChatService chatService, JwtUtil jwtUtil, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.jwtUtil = jwtUtil;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 作用：接收 STOMP 聊天消息，复用 ChatService 处理后把 AI 回复广播到会话主题。
     * 调用方：前端 WebSocket 向 /app/chat.send 发送消息时由 Spring Messaging 自动调用。
     */
    @MessageMapping("/chat.send")
    public void sendMessage(@Valid SendMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = resolveUserId(headerAccessor);
        MessageResponse aiMessage = chatService.sendMessage(userId, request);
        messagingTemplate.convertAndSend(
                "/topic/chat/" + request.getSessionId(),
                ApiResponse.success(aiMessage)
        );
    }

    /**
     * 作用：从 WebSocket 原生请求头解析 Bearer Token 并提取用户 ID。
     * 调用方：sendMessage。
     */
    private Long resolveUserId(SimpMessageHeaderAccessor headerAccessor) {
        String authorization = headerAccessor.getFirstNativeHeader("Authorization");
        if (StringUtils.isBlank(authorization)) {
            authorization = headerAccessor.getFirstNativeHeader("authorization");
        }

        if (!StringUtils.startsWith(authorization, BEARER_PREFIX)) {
            throw new IllegalArgumentException("WebSocket 消息缺少 Authorization Bearer token");
        }

        String token = authorization.substring(BEARER_PREFIX.length());
        if (!jwtUtil.validateToken(token)) {
            throw new IllegalArgumentException("WebSocket token 无效或已过期");
        }

        return jwtUtil.getUserIdFromToken(token);
    }
}
