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

    /**
     * 作用：注入聊天服务。
     * 调用方：Spring 容器启动时自动构造 ChatController。
     */
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 作用：创建一个新的聊天会话，并要求会话绑定到一个用户拥有的漫剧。
     * 调用方：前端创建对话弹窗提交后请求 POST /api/chat/start。
     */
    @PostMapping("/start")
    public ApiResponse<ChatSessionResponse> startSession(@Valid @RequestBody ChatStartRequest request) {
        return ApiResponse.success("会话创建成功", chatService.startSession(SecurityUtils.getCurrentUserId(), request));
    }

    /**
     * 作用：接收用户在对话中的消息，交给 ChatService 保存消息、调用 Python Agent 并执行返回的 Java 方法。
     * 调用方：前端聊天输入框发送消息后请求 POST /api/chat/message。
     */
    @PostMapping("/message")
    public ApiResponse<MessageResponse> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.success(chatService.sendMessage(SecurityUtils.getCurrentUserId(), request));
    }

    /**
     * 作用：查询指定会话的历史消息列表。
     * 调用方：前端进入某个聊天会话或切换会话时请求 GET /api/chat/history/{sessionId}。
     */
    @GetMapping("/history/{sessionId}")
    public ApiResponse<List<MessageResponse>> getHistory(@PathVariable Long sessionId) {
        return ApiResponse.success(chatService.getHistory(sessionId, SecurityUtils.getCurrentUserId()));
    }

    /**
     * 作用：软删除指定聊天会话，将状态标记为 deleted。
     * 调用方：前端会话列表删除按钮请求 DELETE /api/chat/session/{sessionId}。
     */
    @DeleteMapping("/session/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId, SecurityUtils.getCurrentUserId());
        return ApiResponse.success("会话删除成功", null);
    }

    /**
     * 作用：查询当前用户未删除的聊天会话列表。
     * 调用方：前端聊天页初始化、刷新会话列表时请求 GET /api/chat/sessions。
     */
    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionResponse>> getUserSessions() {
        return ApiResponse.success(chatService.getUserSessions(SecurityUtils.getCurrentUserId()));
    }
}
