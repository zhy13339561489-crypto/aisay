package com.aisay.manga.service.impl;

import com.aisay.manga.dto.ai.ChatAgentRequest;
import com.aisay.manga.dto.ai.ChatAgentResponse;
import com.aisay.manga.dto.ai.StoryOutlineGenerateResponse;
import com.aisay.manga.dto.request.ChatStartRequest;
import com.aisay.manga.dto.request.SendMessageRequest;
import com.aisay.manga.dto.response.ChatSessionResponse;
import com.aisay.manga.dto.response.MessageResponse;
import com.aisay.manga.entity.Character;
import com.aisay.manga.entity.ChatSession;
import com.aisay.manga.entity.Message;
import com.aisay.manga.entity.Story;
import com.aisay.manga.repository.CharacterMapper;
import com.aisay.manga.repository.ChatSessionMapper;
import com.aisay.manga.repository.MessageMapper;
import com.aisay.manga.repository.StoryMapper;
import com.aisay.manga.service.ChatService;
import com.aisay.manga.utils.AiEngineClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ChatServiceImpl implements ChatService {

    private static final String SESSION_STATUS_ACTIVE = "active";

    private static final String SESSION_STATUS_DELETED = "deleted";

    private static final String INITIAL_STAGE = "INITIAL";

    private static final String ROLE_USER = "user";

    private static final String ROLE_AI = "ai";

    private static final String METHOD_NONE = "story.none";

    private static final String METHOD_UPDATE_OUTLINE = "story.updateOutline";

    private final ChatSessionMapper chatSessionMapper;

    private final MessageMapper messageMapper;

    private final StoryMapper storyMapper;

    private final CharacterMapper characterMapper;

    private final AiEngineClient aiEngineClient;

    private final ObjectMapper objectMapper;

    public ChatServiceImpl(
            ChatSessionMapper chatSessionMapper,
            MessageMapper messageMapper,
            StoryMapper storyMapper,
            CharacterMapper characterMapper,
            AiEngineClient aiEngineClient,
            ObjectMapper objectMapper
    ) {
        this.chatSessionMapper = chatSessionMapper;
        this.messageMapper = messageMapper;
        this.storyMapper = storyMapper;
        this.characterMapper = characterMapper;
        this.aiEngineClient = aiEngineClient;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ChatSessionResponse startSession(Long userId, ChatStartRequest request) {
        Story story = getOwnedStory(request.getStoryId(), userId);
        LocalDateTime now = LocalDateTime.now();

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setStoryId(story.getId());
        session.setSessionKey(UUID.randomUUID().toString());
        session.setTitle(resolveTitle(request, story));
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
        Story story = getBoundStory(session, userId);
        LocalDateTime now = LocalDateTime.now();

        Message userMessage = new Message();
        userMessage.setSessionId(session.getId());
        userMessage.setRole(ROLE_USER);
        userMessage.setContent(request.getContent());
        userMessage.setMetadata(new HashMap<>());
        userMessage.setCreatedAt(now);
        messageMapper.insert(userMessage);

        String assistantContent = runAgentAndDispatch(userId, session, story, request.getContent());
        Message aiMessage = createAiMessage(session.getId(), assistantContent);
        messageMapper.insert(aiMessage);

        session.setLastActive(LocalDateTime.now());
        chatSessionMapper.updateById(session);

        return toMessageResponse(aiMessage);
    }

    private String runAgentAndDispatch(Long userId, ChatSession session, Story story, String userMessage) {
        try {
            ChatAgentResponse response = aiEngineClient.runChatAgent(new ChatAgentRequest(
                    userId,
                    session.getId(),
                    story.getId(),
                    story.getTitle(),
                    story.getSynopsis(),
                    story.getFullContent(),
                    userMessage
            ));
            dispatchJavaMethod(story, response);
            return resolveText(response.getAssistantMessage(), "已处理你的请求。");
        } catch (RuntimeException ex) {
            return "Python 对话 Agent 暂时不可用，未能更新绑定漫剧《" + story.getTitle() + "》。请确认 FastAPI 服务运行后再试。";
        }
    }

    private void dispatchJavaMethod(Story story, ChatAgentResponse response) {
        String method = resolveText(response.getJavaMethod(), METHOD_NONE);
        if (METHOD_NONE.equals(method)) {
            return;
        }
        if (METHOD_UPDATE_OUTLINE.equals(method)) {
            updateStoryOutline(story, response.getJavaMethodArgs());
            return;
        }
        throw new IllegalArgumentException("Unsupported Java method from Python agent: " + method);
    }

    private void updateStoryOutline(Story story, Map<String, Object> args) {
        if (args == null) {
            return;
        }
        String storySummary = asString(args.get("storySummary"));
        String outline = asString(args.get("outline"));
        if (StringUtils.isNotBlank(storySummary)) {
            story.setSynopsis(storySummary);
        }
        if (StringUtils.isNotBlank(outline)) {
            story.setFullContent(outline);
        }
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);

        Object mainCharacters = args.get("mainCharacters");
        if (mainCharacters != null) {
            List<StoryOutlineGenerateResponse.MainCharacterSetting> settings = objectMapper.convertValue(
                    mainCharacters,
                    new TypeReference<>() {
                    }
            );
            if (!settings.isEmpty()) {
                characterMapper.delete(new LambdaQueryWrapper<Character>().eq(Character::getStoryId, story.getId()));
                saveMainCharacters(story.getId(), settings);
            }
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Message createAiMessage(Long sessionId, String content) {
        Message aiMessage = new Message();
        aiMessage.setSessionId(sessionId);
        aiMessage.setRole(ROLE_AI);
        aiMessage.setContent(content);
        aiMessage.setMetadata(new HashMap<>());
        aiMessage.setCreatedAt(LocalDateTime.now());
        return aiMessage;
    }

    private void saveMainCharacters(Long storyId, List<StoryOutlineGenerateResponse.MainCharacterSetting> mainCharacters) {
        mainCharacters.forEach(setting -> {
            Character character = new Character();
            character.setStoryId(storyId);
            character.setName(resolveText(setting.getName(), "Unnamed character"));
            character.setRole(resolveText(setting.getRole(), "main character"));
            character.setDescription(setting.getDescription());
            character.setPersonality(setting.getPersonality());
            character.setAppearance(setting.getAppearance());
            characterMapper.insert(character);
        });
    }

    private ChatSession getOwnedActiveSession(Long sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || SESSION_STATUS_DELETED.equals(session.getStatus())) {
            throw new NoSuchElementException("Session not found");
        }
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("No permission to access this session");
        }
        return session;
    }

    private Story getBoundStory(ChatSession session, Long userId) {
        if (session.getStoryId() == null) {
            throw new IllegalStateException("This session is not bound to a story. Please create a new session and select a story.");
        }
        return getOwnedStory(session.getStoryId(), userId);
    }

    private Story getOwnedStory(Long storyId, Long userId) {
        Story story = storyMapper.selectById(storyId);
        if (story == null) {
            throw new NoSuchElementException("Story not found");
        }
        if (!story.getUserId().equals(userId)) {
            throw new IllegalArgumentException("No permission to bind this story");
        }
        return story;
    }

    private String resolveTitle(ChatStartRequest request, Story story) {
        if (request == null || StringUtils.isBlank(request.getTitle())) {
            return story.getTitle();
        }
        return request.getTitle();
    }

    private String resolveText(String value, String fallback) {
        if (StringUtils.isBlank(value)) {
            return fallback;
        }
        return value;
    }

    private ChatSessionResponse toSessionResponse(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getStoryId(),
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
