package com.aisay.manga.service.impl;

import com.aisay.manga.config.FeaturePermissionKeys;
import com.aisay.manga.dto.ai.ChatAgentRequest;
import com.aisay.manga.dto.ai.ChatAgentResponse;
import com.aisay.manga.dto.ai.ChatMemoryMessage;
import com.aisay.manga.dto.ai.StoryOutlineGenerateResponse;
import com.aisay.manga.dto.request.AiPromptRequest;
import com.aisay.manga.dto.request.ChatStartRequest;
import com.aisay.manga.dto.request.FeaturePermissionRequest;
import com.aisay.manga.dto.request.SendMessageRequest;
import com.aisay.manga.dto.request.StoryDetailUpdateRequest;
import com.aisay.manga.dto.request.StoryGenerateRequest;
import com.aisay.manga.dto.request.StoryOutlineOptionRequest;
import com.aisay.manga.dto.request.StoryOutlineReviseRequest;
import com.aisay.manga.dto.request.StoryUpdateRequest;
import com.aisay.manga.dto.request.StoryVolumeOutlineReviseRequest;
import com.aisay.manga.dto.request.UserRoleUpdateRequest;
import com.aisay.manga.dto.response.AiPromptResponse;
import com.aisay.manga.dto.response.ChatSessionResponse;
import com.aisay.manga.dto.response.FeaturePermissionResponse;
import com.aisay.manga.dto.response.MessageResponse;
import com.aisay.manga.dto.response.StoryDetailResponse;
import com.aisay.manga.dto.response.StoryOutlineOptionResponse;
import com.aisay.manga.dto.response.StoryResponse;
import com.aisay.manga.dto.response.UserManageResponse;
import com.aisay.manga.entity.Character;
import com.aisay.manga.entity.ChatSession;
import com.aisay.manga.entity.Message;
import com.aisay.manga.entity.Story;
import com.aisay.manga.repository.CharacterMapper;
import com.aisay.manga.repository.ChatRedisRepository;
import com.aisay.manga.repository.StoryMapper;
import com.aisay.manga.service.AiPromptService;
import com.aisay.manga.service.ChatService;
import com.aisay.manga.service.FeaturePermissionService;
import com.aisay.manga.service.PermissionService;
import com.aisay.manga.service.StoryOutlineOptionService;
import com.aisay.manga.service.StoryService;
import com.aisay.manga.service.UserService;
import com.aisay.manga.utils.AiEngineClient;
import com.aisay.manga.utils.ChatIdGenerator;
import com.aisay.manga.utils.ChatPersistPublisher;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    private static final int SHORT_TERM_MEMORY_MESSAGE_LIMIT = 40;

    private static final int DEFAULT_PAGE = 1;

    private static final int DEFAULT_PAGE_SIZE = 10;

    private final ChatRedisRepository chatRedisRepository;

    private final StoryMapper storyMapper;

    private final CharacterMapper characterMapper;

    private final AiEngineClient aiEngineClient;

    private final ObjectMapper objectMapper;

    private final ChatIdGenerator chatIdGenerator;

    private final ChatPersistPublisher chatPersistPublisher;

    private final StoryService storyService;

    private final StoryOutlineOptionService storyOutlineOptionService;

    private final AiPromptService aiPromptService;

    private final UserService userService;

    private final FeaturePermissionService featurePermissionService;

    private final PermissionService permissionService;

    /**
     * 作用：注入聊天、消息、故事、角色数据访问对象，以及 Python AI 引擎客户端。
     * 调用方：Spring 容器启动时自动构造 ChatServiceImpl。
     */
    public ChatServiceImpl(
            ChatRedisRepository chatRedisRepository,
            StoryMapper storyMapper,
            CharacterMapper characterMapper,
            AiEngineClient aiEngineClient,
            ObjectMapper objectMapper,
            ChatIdGenerator chatIdGenerator,
            ChatPersistPublisher chatPersistPublisher,
            StoryService storyService,
            StoryOutlineOptionService storyOutlineOptionService,
            AiPromptService aiPromptService,
            UserService userService,
            FeaturePermissionService featurePermissionService,
            PermissionService permissionService
    ) {
        this.chatRedisRepository = chatRedisRepository;
        this.storyMapper = storyMapper;
        this.characterMapper = characterMapper;
        this.aiEngineClient = aiEngineClient;
        this.objectMapper = objectMapper;
        this.chatIdGenerator = chatIdGenerator;
        this.chatPersistPublisher = chatPersistPublisher;
        this.storyService = storyService;
        this.storyOutlineOptionService = storyOutlineOptionService;
        this.aiPromptService = aiPromptService;
        this.userService = userService;
        this.featurePermissionService = featurePermissionService;
        this.permissionService = permissionService;
    }

    /**
     * 作用：创建一个不绑定具体漫剧的聊天会话。
     * 调用方：ChatController#startSession。
     */
    @Override
    @Transactional
    public ChatSessionResponse startSession(Long userId, ChatStartRequest request) {
        permissionService.requireFeature(userId, FeaturePermissionKeys.CHAT_USE);
        LocalDateTime now = LocalDateTime.now();

        ChatSession session = new ChatSession();
        session.setId(chatIdGenerator.nextId());
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

        chatRedisRepository.saveSession(session);
        chatPersistPublisher.publishSessionUpsert(session);
        return toSessionResponse(session);
    }

    /**
     * 作用：查询会话历史消息，并按创建时间升序转换为前端响应对象。
     * 调用方：ChatController#getHistory。
     */
    @Override
    public List<MessageResponse> getHistory(Long sessionId, Long userId) {
        permissionService.requireFeature(userId, FeaturePermissionKeys.CHAT_USE);
        getOwnedActiveSession(sessionId, userId);
        return chatRedisRepository.getMessages(sessionId)
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
        permissionService.requireFeature(userId, FeaturePermissionKeys.CHAT_USE);
        ChatSession session;
        try {
            session = getOwnedActiveSession(sessionId, userId);
        } catch (NoSuchElementException ex) {
            chatRedisRepository.markSessionDeleted(userId, sessionId);
            return;
        }
        session.setStatus(SESSION_STATUS_DELETED);
        session.setLastActive(LocalDateTime.now());
        chatRedisRepository.saveSession(session);
        chatRedisRepository.markSessionDeleted(userId, sessionId);
        chatPersistPublisher.publishSessionUpsert(session);
    }

    /**
     * 作用：查询当前用户所有未删除会话，并按最近活跃时间倒序返回。
     * 调用方：ChatController#getUserSessions。
     */
    @Override
    public List<ChatSessionResponse> getUserSessions(Long userId) {
        permissionService.requireFeature(userId, FeaturePermissionKeys.CHAT_USE);
        return chatRedisRepository.getUserSessions(userId)
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
        permissionService.requireFeature(userId, FeaturePermissionKeys.CHAT_USE);
        ChatSession session = getOwnedActiveSession(request.getSessionId(), userId);
        LocalDateTime now = LocalDateTime.now();

        Message userMessage = new Message();
        userMessage.setId(chatIdGenerator.nextId());
        userMessage.setSessionId(session.getId());
        userMessage.setRole(ROLE_USER);
        userMessage.setContent(request.getContent());
        userMessage.setMetadata(new HashMap<>());
        userMessage.setCreatedAt(now);
        chatRedisRepository.appendMessage(userMessage);
        chatPersistPublisher.publishMessageInsert(userMessage);

        String assistantContent = runAgentAndDispatch(userId, session, request.getContent());
        Message aiMessage = createAiMessage(session.getId(), assistantContent);
        chatRedisRepository.appendMessage(aiMessage);
        chatPersistPublisher.publishMessageInsert(aiMessage);

        session.setLastActive(LocalDateTime.now());
        chatRedisRepository.saveSession(session);
        chatPersistPublisher.publishSessionUpsert(session);

        return toMessageResponse(aiMessage);
    }

    /**
     * 作用：把当前会话和用户消息发给 Python Agent；当前对话无绑定漫剧，因此不会执行故事修改方法。
     * 调用方：sendMessage。
     */
    private String runAgentAndDispatch(Long userId, ChatSession session, String userMessage) {
        try {
            ChatAgentResponse response = aiEngineClient.runChatAgent(buildChatAgentRequest(userId, session, userMessage));
            persistChatMemory(session.getId(), response);
            String executionResult = dispatchJavaMethod(userId, response);
            return combineAssistantMessage(response, executionResult);
            /*
            return resolveText(response.getAssistantMessage(), "已处理你的请求。");
            */
        } catch (RuntimeException ex) {
            return "Python 对话 Agent 暂时不可用，请确认 FastAPI 服务运行后再试。";
        }
    }

    /**
     * 作用：根据 Python 返回的 javaMethod 做白名单分发，避免 Python 任意指定后端方法。
     * 调用方：runAgentAndDispatch。
     */
    private String dispatchJavaMethod(Long userId, ChatAgentResponse response) {
        /*
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
        */
        String method = resolveText(response.getJavaMethod(), METHOD_NONE);
        if (METHOD_NONE.equals(method)) {
            return null;
        }
        Map<String, Object> args = response.getJavaMethodArgs() == null ? Map.of() : response.getJavaMethodArgs();
        try {
            return switch (method) {
                case METHOD_UPDATE_OUTLINE -> dispatchLegacyStoryUpdateOutline(userId, args);
                case "story.list" -> dispatchStoryList(userId, args);
                case "story.detail" -> formatStoryDetail(storyService.getStoryDetail(requireLongArg(args, "storyId"), userId));
                case "story.generate" -> formatStory(storyService.generateStory(userId, toStoryGenerateRequest(args)));
                case "story.updateBasic" -> formatStory(storyService.updateStory(requireLongArg(args, "storyId"), userId, toStoryUpdateRequest(args)));
                case "story.updateDetail" -> formatStoryDetail(storyService.updateStoryDetail(requireLongArg(args, "storyId"), userId, toStoryDetailUpdateRequest(args)));
                case "story.reviseOutline" -> formatStoryDetail(storyService.reviseStoryOutline(requireLongArg(args, "storyId"), userId, new StoryOutlineReviseRequest(requireStringArg(args, "suggestion"))));
                case "story.generateVolumeOutline" -> formatStoryDetail(storyService.generateVolumeOutline(requireLongArg(args, "storyId"), userId));
                case "story.reviseVolumeOutline" -> formatStoryDetail(storyService.reviseVolumeOutline(requireLongArg(args, "storyId"), userId, new StoryVolumeOutlineReviseRequest(requireStringArg(args, "suggestion"))));
                case "story.generateVolumeSections" -> formatStoryDetail(storyService.generateVolumeSections(requireLongArg(args, "storyId"), requireLongArg(args, "volumeId"), userId));
                case "story.generateSectionAssets" -> formatStoryDetail(storyService.generateSectionAssets(requireLongArg(args, "storyId"), requireLongArg(args, "sectionId"), userId));
                case "story.generateSectionScript" -> formatStoryDetail(storyService.generateSectionScript(requireLongArg(args, "storyId"), requireLongArg(args, "sectionId"), userId));
                case "story.delete" -> dispatchStoryDelete(userId, args);
                case "outlineConfig.list" -> formatOutlineOptions(dispatchOutlineOptionList(userId, args));
                case "outlineConfig.create" -> formatOutlineOption(dispatchOutlineOptionCreate(userId, args));
                case "outlineConfig.update" -> formatOutlineOption(dispatchOutlineOptionUpdate(userId, args));
                case "outlineConfig.delete" -> dispatchOutlineOptionDelete(userId, args);
                case "prompt.list" -> formatPrompts(dispatchPromptList(userId, args));
                case "prompt.detail" -> formatPrompt(dispatchPromptDetail(userId, args));
                case "prompt.create" -> formatPrompt(dispatchPromptCreate(userId, args));
                case "prompt.update" -> formatPrompt(dispatchPromptUpdate(userId, args));
                case "prompt.delete" -> dispatchPromptDelete(userId, args);
                case "user.list" -> formatUsers(userService.listUsers(userId));
                case "user.updateRole" -> formatUser(userService.updateUserRole(userId, requireLongArg(args, "targetUserId"), new UserRoleUpdateRequest(requireStringArg(args, "role"))));
                case "featurePermission.list" -> formatFeaturePermissions(featurePermissionService.listPermissions(userId));
                case "featurePermission.update" -> formatFeaturePermission(dispatchFeaturePermissionUpdate(userId, args));
                default -> "当前 Java 白名单还不支持该操作：" + method;
            };
        } catch (RuntimeException ex) {
            return "操作没有执行成功：" + ex.getMessage();
        }
    }

    /**
     * 作用：执行 story.updateOutline，把 Python 返回的故事摘要、大纲和角色设定同步写入数据库。
     * 调用方：dispatchJavaMethod。
     */
    private ChatAgentRequest buildChatAgentRequest(Long userId, ChatSession session, String userMessage) {
        List<Message> allMessages = chatRedisRepository.getMessages(session.getId());
        int summaryCursor = Math.min(chatRedisRepository.getSummaryCursor(session.getId()), allMessages.size());
        int summaryEnd = Math.max(summaryCursor, allMessages.size() - SHORT_TERM_MEMORY_MESSAGE_LIMIT);
        List<Message> summaryCandidateMessages = summaryEnd > summaryCursor
                ? new ArrayList<>(allMessages.subList(summaryCursor, summaryEnd))
                : List.of();
        List<Message> recentMessages = tailMessages(allMessages, SHORT_TERM_MEMORY_MESSAGE_LIMIT);

        ChatAgentRequest request = new ChatAgentRequest();
        request.setUserId(userId);
        request.setSessionId(session.getId());
        request.setStoryId(session.getStoryId());
        request.setTitle(session.getTitle());
        request.setUserMessage(userMessage);
        request.setUserRole(userService.getProfile(userId).getRole());
        request.setRecentMessages(toMemoryMessages(recentMessages));
        request.setLongTermMemory(chatRedisRepository.getLongTermMemory(session.getId()));
        request.setKeyFacts(chatRedisRepository.getKeyFacts(session.getId()));
        request.setSummaryCandidateMessages(toMemoryMessages(summaryCandidateMessages));
        request.setSummaryCandidateCount(summaryEnd);
        return request;
    }

    private void persistChatMemory(Long sessionId, ChatAgentResponse response) {
        if (StringUtils.isNotBlank(response.getLongTermMemory())) {
            chatRedisRepository.saveLongTermMemory(sessionId, response.getLongTermMemory());
        }
        if (response.getKeyFacts() != null && !response.getKeyFacts().isEmpty()) {
            chatRedisRepository.saveKeyFacts(sessionId, response.getKeyFacts());
        }
        if (response.getSummarizedMessageCount() != null) {
            chatRedisRepository.saveSummaryCursor(sessionId, response.getSummarizedMessageCount());
        }
    }

    private List<Message> tailMessages(List<Message> messages, int limit) {
        if (messages.size() <= limit) {
            return messages;
        }
        return new ArrayList<>(messages.subList(messages.size() - limit, messages.size()));
    }

    private List<ChatMemoryMessage> toMemoryMessages(List<Message> messages) {
        return messages.stream()
                .map(message -> new ChatMemoryMessage(
                        message.getRole(),
                        message.getContent(),
                        message.getCreatedAt() == null ? null : message.getCreatedAt().toString()
                ))
                .toList();
    }

    private String combineAssistantMessage(ChatAgentResponse response, String executionResult) {
        String assistantMessage = resolveText(response.getAssistantMessage(), "已理解你的请求。");
        if (StringUtils.isBlank(executionResult)) {
            return assistantMessage;
        }
        return assistantMessage + "\n\n执行结果：\n" + executionResult;
    }

    private String dispatchLegacyStoryUpdateOutline(Long userId, Map<String, Object> args) {
        Story story = getOwnedStory(requireLongArg(args, "storyId"), userId);
        updateStoryOutline(story, args);
        return "已更新漫剧大纲：" + story.getTitle();
    }

    private String dispatchStoryList(Long userId, Map<String, Object> args) {
        int page = argInteger(args, "page", DEFAULT_PAGE);
        int size = argInteger(args, "size", DEFAULT_PAGE_SIZE);
        return formatStoryPage(storyService.getUserStories(userId, page, size));
    }

    private String dispatchStoryDelete(Long userId, Map<String, Object> args) {
        Long storyId = requireLongArg(args, "storyId");
        storyService.deleteStory(storyId, userId);
        return "已删除漫剧，ID：" + storyId;
    }

    private List<StoryOutlineOptionResponse> dispatchOutlineOptionList(Long userId, Map<String, Object> args) {
        permissionService.requireFeature(userId, FeaturePermissionKeys.OUTLINE_CONFIG_MANAGE);
        return storyOutlineOptionService.listOptions(argString(args, "type"), argBoolean(args, "enabled"));
    }

    private StoryOutlineOptionResponse dispatchOutlineOptionCreate(Long userId, Map<String, Object> args) {
        permissionService.requireFeature(userId, FeaturePermissionKeys.OUTLINE_CONFIG_MANAGE);
        return storyOutlineOptionService.createOption(toStoryOutlineOptionRequest(args));
    }

    private StoryOutlineOptionResponse dispatchOutlineOptionUpdate(Long userId, Map<String, Object> args) {
        permissionService.requireFeature(userId, FeaturePermissionKeys.OUTLINE_CONFIG_MANAGE);
        return storyOutlineOptionService.updateOption(requireLongArg(args, "id"), toStoryOutlineOptionRequest(args));
    }

    private String dispatchOutlineOptionDelete(Long userId, Map<String, Object> args) {
        permissionService.requireFeature(userId, FeaturePermissionKeys.OUTLINE_CONFIG_MANAGE);
        Long id = requireLongArg(args, "id");
        storyOutlineOptionService.deleteOption(id);
        return "已删除大纲配置，ID：" + id;
    }

    private List<AiPromptResponse> dispatchPromptList(Long userId, Map<String, Object> args) {
        permissionService.requireFeature(userId, FeaturePermissionKeys.PROMPT_MANAGE);
        return aiPromptService.listPrompts(argString(args, "category"), argBoolean(args, "enabled"), argString(args, "keyword"));
    }

    private AiPromptResponse dispatchPromptDetail(Long userId, Map<String, Object> args) {
        permissionService.requireFeature(userId, FeaturePermissionKeys.PROMPT_MANAGE);
        return aiPromptService.getPrompt(requireLongArg(args, "id"));
    }

    private AiPromptResponse dispatchPromptCreate(Long userId, Map<String, Object> args) {
        permissionService.requireFeature(userId, FeaturePermissionKeys.PROMPT_MANAGE);
        return aiPromptService.createPrompt(objectMapper.convertValue(args, AiPromptRequest.class));
    }

    private AiPromptResponse dispatchPromptUpdate(Long userId, Map<String, Object> args) {
        permissionService.requireFeature(userId, FeaturePermissionKeys.PROMPT_MANAGE);
        return aiPromptService.updatePrompt(requireLongArg(args, "id"), objectMapper.convertValue(args, AiPromptRequest.class));
    }

    private String dispatchPromptDelete(Long userId, Map<String, Object> args) {
        permissionService.requireFeature(userId, FeaturePermissionKeys.PROMPT_MANAGE);
        Long id = requireLongArg(args, "id");
        aiPromptService.deletePrompt(id);
        return "已删除 Prompt，ID：" + id;
    }

    private FeaturePermissionResponse dispatchFeaturePermissionUpdate(Long userId, Map<String, Object> args) {
        FeaturePermissionRequest request = toFeaturePermissionRequest(args);
        Long id = argLong(args, "id");
        if (id != null) {
            return featurePermissionService.updatePermission(userId, id, request);
        }
        return featurePermissionService.updatePermissionByFeatureKey(userId, requireStringArg(args, "featureKey"), request);
    }

    private StoryGenerateRequest toStoryGenerateRequest(Map<String, Object> args) {
        return new StoryGenerateRequest(requireStringArg(args, "genre"), requireStringArg(args, "style"), argString(args, "plot"));
    }

    private StoryUpdateRequest toStoryUpdateRequest(Map<String, Object> args) {
        return new StoryUpdateRequest(argString(args, "title"), argString(args, "genre"), argString(args, "style"), argString(args, "synopsis"));
    }

    private StoryDetailUpdateRequest toStoryDetailUpdateRequest(Map<String, Object> args) {
        return objectMapper.convertValue(args, StoryDetailUpdateRequest.class);
    }

    private StoryOutlineOptionRequest toStoryOutlineOptionRequest(Map<String, Object> args) {
        return new StoryOutlineOptionRequest(
                requireStringArg(args, "type"),
                requireStringArg(args, "name"),
                argString(args, "description"),
                argInteger(args, "sortOrder", 0),
                argBoolean(args, "enabled")
        );
    }

    private FeaturePermissionRequest toFeaturePermissionRequest(Map<String, Object> args) {
        FeaturePermissionRequest request = new FeaturePermissionRequest();
        request.setAllowedRoles(parseAllowedRoles(args.get("allowedRoles")));
        request.setEnabled(argBoolean(args, "enabled"));
        return request;
    }

    private List<String> parseAllowedRoles(Object rawRoles) {
        List<String> roles = new ArrayList<>();
        if (rawRoles instanceof List<?> roleList) {
            roleList.stream()
                    .map(role -> role == null ? null : String.valueOf(role).trim())
                    .map(this::normalizeRoleArg)
                    .filter(StringUtils::isNotBlank)
                    .forEach(roles::add);
        } else if (rawRoles instanceof String text && StringUtils.isNotBlank(text)) {
            for (String role : text.split("[,，、\\s]+")) {
                if (StringUtils.isNotBlank(role)) {
                    roles.add(normalizeRoleArg(role.trim()));
                }
            }
        }
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("缺少参数：allowedRoles");
        }
        return roles;
    }

    private String normalizeRoleArg(String role) {
        if (StringUtils.isBlank(role)) {
            return role;
        }
        return switch (role.trim().toUpperCase()) {
            case "ROOT", "超级管理员" -> "ROOT";
            case "ADMIN", "管理员" -> "ADMIN";
            case "USER", "普通用户", "用户" -> "USER";
            default -> role.trim();
        };
    }

    private String formatStoryPage(Page<StoryResponse> page) {
        if (page.getRecords().isEmpty()) {
            return "当前没有查询到漫剧。";
        }
        List<String> rows = page.getRecords().stream()
                .map(story -> story.getId() + " - " + story.getTitle() + "（" + story.getStatus() + "）")
                .toList();
        return "共 " + page.getTotal() + " 部漫剧：\n" + String.join("\n", rows);
    }

    private String formatStory(StoryResponse story) {
        return "漫剧：" + story.getId() + " - " + story.getTitle() + "，状态：" + story.getStatus();
    }

    private String formatStoryDetail(StoryDetailResponse story) {
        int volumeCount = story.getVolumeOutlines() == null ? 0 : story.getVolumeOutlines().size();
        int characterCount = story.getCharacters() == null ? 0 : story.getCharacters().size();
        return "漫剧详情：" + story.getId() + " - " + story.getTitle()
                + "，状态：" + story.getStatus()
                + "，角色数：" + characterCount
                + "，分卷数：" + volumeCount;
    }

    private String formatOutlineOptions(List<StoryOutlineOptionResponse> options) {
        if (options.isEmpty()) {
            return "没有查询到大纲配置。";
        }
        return String.join("\n", options.stream()
                .map(option -> option.getId() + " - " + option.getType() + " - " + option.getName() + "（enabled=" + option.getEnabled() + "）")
                .toList());
    }

    private String formatOutlineOption(StoryOutlineOptionResponse option) {
        return "大纲配置：" + option.getId() + " - " + option.getType() + " - " + option.getName() + "（enabled=" + option.getEnabled() + "）";
    }

    private String formatPrompts(List<AiPromptResponse> prompts) {
        if (prompts.isEmpty()) {
            return "没有查询到 Prompt。";
        }
        return String.join("\n", prompts.stream()
                .map(prompt -> prompt.getId() + " - " + prompt.getPromptKey() + " - " + prompt.getPromptName() + "（" + prompt.getPromptScope() + "）")
                .toList());
    }

    private String formatPrompt(AiPromptResponse prompt) {
        return "Prompt：" + prompt.getId() + " - " + prompt.getPromptKey() + " - " + prompt.getPromptName()
                + "（" + prompt.getPromptScope() + "，enabled=" + prompt.getEnabled() + "）";
    }

    private String formatUsers(List<UserManageResponse> users) {
        if (users.isEmpty()) {
            return "没有查询到用户。";
        }
        return String.join("\n", users.stream()
                .map(user -> user.getId() + " - " + user.getUsername() + " - " + user.getRole())
                .toList());
    }

    private String formatUser(UserManageResponse user) {
        return "用户：" + user.getId() + " - " + user.getUsername() + "，权限：" + user.getRole();
    }

    private String formatFeaturePermissions(List<FeaturePermissionResponse> permissions) {
        if (permissions.isEmpty()) {
            return "没有查询到功能权限配置。";
        }
        return String.join("\n", permissions.stream()
                .map(permission -> permission.getId() + " - " + permission.getFeatureKey()
                        + " - " + permission.getFeatureName()
                        + "，allowedRoles=" + String.join(",", permission.getAllowedRoles())
                        + "，enabled=" + permission.getEnabled())
                .toList());
    }

    private String formatFeaturePermission(FeaturePermissionResponse permission) {
        return "功能权限：" + permission.getId() + " - " + permission.getFeatureKey()
                + " - " + permission.getFeatureName()
                + "，allowedRoles=" + String.join(",", permission.getAllowedRoles())
                + "，enabled=" + permission.getEnabled();
    }

    private Long requireLongArg(Map<String, Object> args, String key) {
        Long value = argLong(args, key);
        if (value == null) {
            throw new IllegalArgumentException("缺少参数：" + key);
        }
        return value;
    }

    private String requireStringArg(Map<String, Object> args, String key) {
        String value = argString(args, key);
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("缺少参数：" + key);
        }
        return value;
    }

    private Long argLong(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            return Long.valueOf(text.trim());
        }
        return null;
    }

    private Integer argInteger(Map<String, Object> args, String key, Integer fallback) {
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            return Integer.valueOf(text.trim());
        }
        return fallback;
    }

    private Boolean argBoolean(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            return Boolean.valueOf(text.trim());
        }
        return null;
    }

    private String argString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : String.valueOf(value);
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
        aiMessage.setId(chatIdGenerator.nextId());
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
        ChatSession session = chatRedisRepository.getSession(sessionId);
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
