package com.aisay.manga.service;

import com.aisay.manga.dto.request.ChatStartRequest;
import com.aisay.manga.dto.request.SendMessageRequest;
import com.aisay.manga.dto.response.ChatSessionResponse;
import com.aisay.manga.dto.response.MessageResponse;

import java.util.List;

public interface ChatService {

    ChatSessionResponse startSession(Long userId, ChatStartRequest request);

    List<MessageResponse> getHistory(Long sessionId, Long userId);

    void deleteSession(Long sessionId, Long userId);

    List<ChatSessionResponse> getUserSessions(Long userId);

    MessageResponse sendMessage(Long userId, SendMessageRequest request);
}
