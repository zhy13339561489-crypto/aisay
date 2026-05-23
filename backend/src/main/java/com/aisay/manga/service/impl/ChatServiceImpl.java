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

    /**
     * 作用：注入聊天、消息、故事、角色数据访问对象，以及 Python AI 引擎客户端。
     * 调用方：Spring 容器启动时自动构造 ChatServiceImpl。
     */
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

    /**
     * 作用：创建一个不绑定具体漫剧的聊天会话。
     * 调用方：ChatController#startSession。
     */
    @Override
    @Transactional
    public ChatSessionResponse startSession(Long userId, ChatStartRequest request) {
        LocalDateTime now = LocalDateTime.now();

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setStoryId(null);
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

    /**
     * 作用：查询会话历史消息，并按创建时间升序转换为前端响应对象。
     * 调用方：ChatController#getHistory。
     */
    @Override
    public List<MessageResponse> getHistory(Long sessionId, Long userId) {
        getOwnedActiveSession(sessionId, userId);
        return messageMapper.selectBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    /**
     * 作用：软删除聊天会话，保留数据库记录但不再展示给用户。
     * 调用方：ChatController#deleteSession。
     */
    @Override
    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        ChatSession session = getOwnedActiveSession(sessionId, userId);
        session.setStatus(SESSION_STATUS_DELETED);
        session.setLastActive(LocalDateTime.now());
        chatSessionMapper.updateById(session);
    }

    /**
     * 作用：查询当前用户所有未删除会话，并按最近活跃时间倒序返回。
     * 调用方：ChatController#getUserSessions。
     */
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

    /**
     * 作用：保存用户消息，调用 Python Chat Agent，并保存 AI 回复。对话不再直接绑定或修改具体漫剧。
     * 调用方：ChatController#sendMessage。
     */
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

        String assistantContent = runAgentAndDispatch(userId, session, request.getContent());
        Message aiMessage = createAiMessage(session.getId(), assistantContent);
        messageMapper.insert(aiMessage);

        session.setLastActive(LocalDateTime.now());
        chatSessionMapper.updateById(session);

        return toMessageResponse(aiMessage);
    }

    /**
     * 作用：把当前会话和用户消息发给 Python Agent；当前对话无绑定漫剧，因此不会执行故事修改方法。
     * 调用方：sendMessage。
     */
    private String runAgentAndDispatch(Long userId, ChatSession session, String userMessage) {
        try {
            ChatAgentResponse response = aiEngineClient.runChatAgent(new ChatAgentRequest(
                    userId,
                    session.getId(),
                    null,
                    session.getTitle(),
                    null,
                    null,
                    null,
                    null,
                    userMessage
            ));
            dispatchJavaMethod(null, response);
            return resolveText(response.getAssistantMessage(), "已处理你的请求。");
        } catch (RuntimeException ex) {
            return "Python 对话 Agent 暂时不可用，请确认 FastAPI 服务运行后再试。";
        }
    }

    /**
     * 作用：根据 Python 返回的 javaMethod 做白名单分发，避免 Python 任意指定后端方法。
     * 调用方：runAgentAndDispatch。
     */
    private void dispatchJavaMethod(Story story, ChatAgentResponse response) {
        String method = resolveText(response.getJavaMethod(), METHOD_NONE);
        if (METHOD_NONE.equals(method)) {
            return;
        }
        if (story == null) {
            return;
        }
        if (METHOD_UPDATE_OUTLINE.equals(method)) {
            updateStoryOutline(story, response.getJavaMethodArgs());
            return;
        }
        throw new IllegalArgumentException("Unsupported Java method from Python agent: " + method);
    }

    /**
     * 作用：执行 story.updateOutline，把 Python 返回的故事摘要、大纲和角色设定同步写入数据库。
     * 调用方：dispatchJavaMethod。
     */
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

    /**
     * 作用：把 Python 返回参数中的任意对象安全转成字符串。
     * 调用方：updateStoryOutline。
     */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 作用：构造 AI 角色消息实体，等待 sendMessage 统一入库。
     * 调用方：sendMessage。
     */
    private Message createAiMessage(Long sessionId, String content) {
        Message aiMessage = new Message();
        aiMessage.setSessionId(sessionId);
        aiMessage.setRole(ROLE_AI);
        aiMessage.setContent(content);
        aiMessage.setMetadata(new HashMap<>());
        aiMessage.setCreatedAt(LocalDateTime.now());
        return aiMessage;
    }

    /**
     * 作用：将结构化主要角色设定保存到 characters 表。
     * 调用方：updateStoryOutline。
     */
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

    /**
     * 作用：校验会话存在、未删除且属于当前用户。
     * 调用方：getHistory、deleteSession、sendMessage。
     */
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

    /**
     * 作用：校验漫剧存在且属于当前用户。
     * 调用方：保留给未来需要显式选择故事的工具调用使用。
     */
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

    /**
     * 作用：决定新会话标题，用户未填写时使用通用默认标题。
     * 调用方：startSession。
     */
    private String resolveTitle(ChatStartRequest request) {
        if (request == null || StringUtils.isBlank(request.getTitle())) {
            return "新对话";
        }
        return request.getTitle();
    }

    /**
     * 作用：为空字符串或空值提供兜底文本。
     * 调用方：runAgentAndDispatch、saveMainCharacters、dispatchJavaMethod。
     */
    private String resolveText(String value, String fallback) {
        if (StringUtils.isBlank(value)) {
            return fallback;
        }
        return value;
    }

    /**
     * 作用：将 ChatSession 实体转换为前端会话响应 DTO。
     * 调用方：startSession、getUserSessions。
     */
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

    /**
     * 作用：将 Message 实体转换为前端消息响应 DTO。
     * 调用方：getHistory、sendMessage。
     */
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
