package com.aisay.manga.service;

import com.aisay.manga.dto.request.ChatStartRequest;
import com.aisay.manga.dto.request.SendMessageRequest;
import com.aisay.manga.dto.response.ChatSessionResponse;
import com.aisay.manga.dto.response.MessageResponse;

import java.util.List;

public interface ChatService {

    /**
     * 作用：创建不绑定具体漫剧的聊天会话。
     * 调用方：ChatController#startSession。
     */
    ChatSessionResponse startSession(Long userId, ChatStartRequest request);

    /**
     * 作用：查询指定会话的历史消息。
     * 调用方：ChatController#getHistory。
     */
    List<MessageResponse> getHistory(Long sessionId, Long userId);

    /**
     * 作用：软删除指定会话。
     * 调用方：ChatController#deleteSession。
     */
    void deleteSession(Long sessionId, Long userId);

    /**
     * 作用：查询当前用户的会话列表。
     * 调用方：ChatController#getUserSessions。
     */
    List<ChatSessionResponse> getUserSessions(Long userId);

    /**
     * 作用：发送聊天消息，触发 Python Agent 并保存 AI 回复。
     * 调用方：ChatController#sendMessage。
     */
    MessageResponse sendMessage(Long userId, SendMessageRequest request);
}
