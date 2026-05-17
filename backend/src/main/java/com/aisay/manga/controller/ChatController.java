package com.aisay.manga.controller;

import com.aisay.manga.dto.request.ChatStartRequest;
import com.aisay.manga.dto.request.SendMessageRequest;
import com.aisay.manga.dto.response.ApiResponse;
import com.aisay.manga.dto.response.ChatSessionResponse;
import com.aisay.manga.dto.response.MessageResponse;
import com.aisay.manga.service.ChatService;
import com.aisay.manga.utils.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/start")
    public ApiResponse<ChatSessionResponse> startSession(@Valid @RequestBody(required = false) ChatStartRequest request) {
        return ApiResponse.success("会话创建成功", chatService.startSession(SecurityUtils.getCurrentUserId(), request));
    }

    @PostMapping("/message")
    public ApiResponse<MessageResponse> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.success(chatService.sendMessage(SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping("/history/{sessionId}")
    public ApiResponse<List<MessageResponse>> getHistory(@PathVariable Long sessionId) {
        return ApiResponse.success(chatService.getHistory(sessionId, SecurityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/session/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId, SecurityUtils.getCurrentUserId());
        return ApiResponse.success("会话删除成功", null);
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionResponse>> getUserSessions() {
        return ApiResponse.success(chatService.getUserSessions(SecurityUtils.getCurrentUserId()));
    }
}
