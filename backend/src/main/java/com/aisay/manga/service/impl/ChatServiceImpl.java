package com.aisay.manga.service.impl;

import com.aisay.manga.dto.request.ChatStartRequest;
import com.aisay.manga.dto.request.SendMessageRequest;
import com.aisay.manga.dto.response.ChatSessionResponse;
import com.aisay.manga.dto.response.MessageResponse;
import com.aisay.manga.entity.ChatSession;
import com.aisay.manga.entity.Message;
import com.aisay.manga.repository.ChatSessionMapper;
import com.aisay.manga.repository.MessageMapper;
import com.aisay.manga.service.ChatService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ChatServiceImpl implements ChatService {

    private static final String SESSION_STATUS_ACTIVE = "active";

    private static final String SESSION_STATUS_DELETED = "deleted";

    private static final String INITIAL_STAGE = "INITIAL";

    private static final String ROLE_USER = "user";

    private static final String ROLE_AI = "ai";

    private static final String MOCK_AI_REPLY = "你好！我是AI漫剧创作助手。关于漫剧创作的功能正在开发中，敬请期待！";

    private final ChatSessionMapper chatSessionMapper;

    private final MessageMapper messageMapper;

    public ChatServiceImpl(ChatSessionMapper chatSessionMapper, MessageMapper messageMapper) {
        this.chatSessionMapper = chatSessionMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    @Transactional
    public ChatSessionResponse startSession(Long userId, ChatStartRequest request) {
        LocalDateTime now = LocalDateTime.now();
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setSessionKey(UUID.randomUUID().toString());
        session.setTitle(resolveTitle(request));
        session.setContextData(new HashMap<>());
        session.setCurrentStage(INITIAL_STAGE);
        session.setProgressPercentage(0);
        session.setStatus(SESSION_STATUS_ACTIVE);
        session.setStartedAt(now);
        session.setLastActive(now);

        chatSessionMapper.insert(session);
        return toSessionResponse(session);
    }

    @Override
    public List<MessageResponse> getHistory(Long sessionId, Long userId) {
        getOwnedActiveSession(sessionId, userId);
        return messageMapper.selectBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        ChatSession session = getOwnedActiveSession(sessionId, userId);
        session.setStatus(SESSION_STATUS_DELETED);
        session.setLastActive(LocalDateTime.now());
        chatSessionMapper.updateById(session);
    }

    @Override
    public List<ChatSessionResponse> getUserSessions(Long userId) {
        return chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .ne(ChatSession::getStatus, SESSION_STATUS_DELETED)
                        .orderByDesc(ChatSession::getLastActive)
                        .orderByDesc(ChatSession::getId))
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Override
    @Transactional
    public MessageResponse sendMessage(Long userId, SendMessageRequest request) {
        ChatSession session = getOwnedActiveSession(request.getSessionId(), userId);
        LocalDateTime now = LocalDateTime.now();

        Message userMessage = new Message();
        userMessage.setSessionId(session.getId());
        userMessage.setRole(ROLE_USER);
        userMessage.setContent(request.getContent());
        userMessage.setMetadata(new HashMap<>());
        userMessage.setCreatedAt(now);
        messageMapper.insert(userMessage);

        Message aiMessage = new Message();
        aiMessage.setSessionId(session.getId());
        aiMessage.setRole(ROLE_AI);
        aiMessage.setContent(MOCK_AI_REPLY);
        aiMessage.setMetadata(new HashMap<>());
        aiMessage.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(aiMessage);

        session.setLastActive(LocalDateTime.now());
        chatSessionMapper.updateById(session);

        return toMessageResponse(aiMessage);
    }

    private ChatSession getOwnedActiveSession(Long sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || SESSION_STATUS_DELETED.equals(session.getStatus())) {
            throw new NoSuchElementException("会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该会话");
        }
        return session;
    }

    private String resolveTitle(ChatStartRequest request) {
        if (request == null || StringUtils.isBlank(request.getTitle())) {
            return "新对话";
        }
        return request.getTitle();
    }

    private ChatSessionResponse toSessionResponse(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getSessionKey(),
                session.getTitle(),
                session.getStatus(),
                session.getStartedAt(),
                session.getLastActive()
        );
    }

    private MessageResponse toMessageResponse(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getSessionId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
