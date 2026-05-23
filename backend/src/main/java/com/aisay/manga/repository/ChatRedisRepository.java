package com.aisay.manga.repository;

import com.aisay.manga.entity.ChatSession;
import com.aisay.manga.entity.Message;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class ChatRedisRepository {

    private static final String SESSION_KEY_PREFIX = "aisay:chat:session:";

    private static final String USER_SESSION_KEY_PREFIX = "aisay:chat:user:";

    private static final String MESSAGE_KEY_PREFIX = "aisay:chat:messages:";

    private static final String LONG_TERM_MEMORY_KEY_PREFIX = "aisay:chat:memory:long:";

    private static final String KEY_FACTS_KEY_PREFIX = "aisay:chat:memory:facts:";

    private static final String SUMMARY_CURSOR_KEY_PREFIX = "aisay:chat:memory:summary-cursor:";

    private static final String SESSION_STATUS_DELETED = "deleted";

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final ChatSessionMapper chatSessionMapper;

    private final MessageMapper messageMapper;

    public ChatRedisRepository(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ChatSessionMapper chatSessionMapper,
            MessageMapper messageMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.messageMapper = messageMapper;
    }

    /**
     * 作用：把会话写入 Redis，并维护用户会话有序集合。
     * 调用方：ChatServiceImpl 创建、更新和删除会话时。
     */
    public void saveSession(ChatSession session) {
        writeJson(sessionKey(session.getId()), session);
        String userSessionsKey = userSessionsKey(session.getUserId());
        if (SESSION_STATUS_DELETED.equals(session.getStatus())) {
            redisTemplate.opsForZSet().remove(userSessionsKey, String.valueOf(session.getId()));
            return;
        }
        redisTemplate.opsForZSet().add(
                userSessionsKey,
                String.valueOf(session.getId()),
                session.getLastActive() == null ? 0D : (double) toEpochMillis(session)
        );
    }

    /**
     * 作用：按 ID 从 Redis 读取会话，未命中时从 MySQL 回填。
     * 调用方：ChatServiceImpl 校验会话归属和状态时。
     */
    public ChatSession getSession(Long sessionId) {
        ChatSession cached = readJson(sessionKey(sessionId), ChatSession.class);
        if (cached != null) {
            return cached;
        }

        try {
            ChatSession persisted = chatSessionMapper.selectById(sessionId);
            if (persisted != null) {
                saveSession(persisted);
            }
            return persisted;
        } catch (RuntimeException ex) {
            // Redis 是聊天实时数据源；MySQL 回填失败时不阻断主链路。
            return null;
        }
    }

    /**
     * 作用：查询某个用户的会话列表，以 Redis 为主，并合并 MySQL 旧数据。
     * 调用方：ChatServiceImpl#getUserSessions。
     */
    public List<ChatSession> getUserSessions(Long userId) {
        Map<Long, ChatSession> merged = new LinkedHashMap<>();
        String userSessionsKey = userSessionsKey(userId);
        Set<String> cachedSessionIds = redisTemplate.opsForZSet().reverseRange(userSessionsKey, 0, -1);
        if (cachedSessionIds == null) {
            cachedSessionIds = Set.of();
        }
        for (String sessionIdValue : cachedSessionIds) {
            ChatSession session = getSession(Long.valueOf(sessionIdValue));
            if (isVisibleSession(session, userId)) {
                merged.put(session.getId(), session);
            }
        }

        try {
            List<ChatSession> persistedSessions = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                    .eq(ChatSession::getUserId, userId)
                    .ne(ChatSession::getStatus, SESSION_STATUS_DELETED)
                    .orderByDesc(ChatSession::getLastActive)
                    .orderByDesc(ChatSession::getId));
            for (ChatSession session : persistedSessions) {
                saveSession(session);
                merged.putIfAbsent(session.getId(), session);
            }
        } catch (RuntimeException ex) {
            // MySQL 只是异步持久化副本；失败时保留 Redis 中的实时会话列表。
        }

        List<ChatSession> sessions = new ArrayList<>(merged.values());
        sessions.sort((left, right) -> {
            if (left.getLastActive() == null && right.getLastActive() == null) {
                return Long.compare(right.getId(), left.getId());
            }
            if (left.getLastActive() == null) {
                return 1;
            }
            if (right.getLastActive() == null) {
                return -1;
            }
            int timeCompare = right.getLastActive().compareTo(left.getLastActive());
            return timeCompare != 0 ? timeCompare : Long.compare(right.getId(), left.getId());
        });
        return sessions;
    }

    /**
     * 作用：把聊天消息追加到 Redis List。
     * 调用方：ChatServiceImpl 保存用户消息和 AI 回复时。
     */
    public void appendMessage(Message message) {
        redisTemplate.opsForList().rightPush(messageKey(message.getSessionId()), toJson(message));
    }

    /**
     * 作用：查询会话消息历史，以 Redis 为主，未命中时从 MySQL 回填。
     * 调用方：ChatServiceImpl#getHistory。
     */
    public List<Message> getMessages(Long sessionId) {
        String messageKey = messageKey(sessionId);
        List<String> cachedMessages = redisTemplate.opsForList().range(messageKey, 0, -1);
        if (cachedMessages != null && !cachedMessages.isEmpty()) {
            List<Message> messages = new ArrayList<>();
            for (String cachedMessage : cachedMessages) {
                messages.add(fromJson(cachedMessage, Message.class));
            }
            return messages;
        }

        try {
            List<Message> persistedMessages = messageMapper.selectBySessionIdOrderByCreatedAtAsc(sessionId);
            for (Message message : persistedMessages) {
                appendMessage(message);
            }
            return persistedMessages;
        } catch (RuntimeException ex) {
            // MySQL 回填失败时返回空历史，避免影响新 Redis 会话的使用。
            return List.of();
        }
    }

    /**
     * 作用：读取当前会话的长期记忆摘要。
     * 调用方：ChatServiceImpl 调用 Python 智能对话模块前。
     */
    public String getLongTermMemory(Long sessionId) {
        return redisTemplate.opsForValue().get(longTermMemoryKey(sessionId));
    }

    /**
     * 作用：保存当前会话的长期记忆摘要。
     * 调用方：ChatServiceImpl 收到 Python 记忆更新结果后。
     */
    public void saveLongTermMemory(Long sessionId, String longTermMemory) {
        if (longTermMemory != null) {
            redisTemplate.opsForValue().set(longTermMemoryKey(sessionId), longTermMemory);
        }
    }

    /**
     * 作用：读取当前会话的关键真实信息 JSON。
     * 调用方：ChatServiceImpl 调用 Python 智能对话模块前。
     */
    public Map<String, Object> getKeyFacts(Long sessionId) {
        String key = keyFactsKey(sessionId);
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            if (entries.isEmpty()) {
                return Map.of();
            }
            Map<String, Object> facts = new LinkedHashMap<>();
            entries.forEach((field, value) -> facts.put(String.valueOf(field), fromJson(String.valueOf(value), Object.class)));
            return facts;
        } catch (RuntimeException ex) {
            String legacyValue = redisTemplate.opsForValue().get(key);
            Map<String, Object> legacyFacts = parseObjectMap(legacyValue);
            if (!legacyFacts.isEmpty()) {
                saveKeyFacts(sessionId, legacyFacts);
            }
            return legacyFacts;
        }
    }

    /**
     * 作用：保存当前会话的关键真实信息 JSON。
     * 调用方：ChatServiceImpl 收到 Python 记忆更新结果后。
     */
    public void saveKeyFacts(Long sessionId, Map<String, Object> keyFacts) {
        if (keyFacts == null) {
            return;
        }
        String key = keyFactsKey(sessionId);
        redisTemplate.delete(key);
        Map<String, String> serializedFacts = new LinkedHashMap<>();
        keyFacts.forEach((field, value) -> {
            if (field != null && value != null) {
                serializedFacts.put(field, toJson(value));
            }
        });
        if (!serializedFacts.isEmpty()) {
            redisTemplate.opsForHash().putAll(key, serializedFacts);
        }
    }

    /**
     * 作用：读取已经被长期记忆摘要覆盖的消息数量。
     * 调用方：ChatServiceImpl 判断哪些旧消息需要继续摘要。
     */
    public int getSummaryCursor(Long sessionId) {
        String value = redisTemplate.opsForValue().get(summaryCursorKey(sessionId));
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * 作用：保存已经被长期记忆摘要覆盖的消息数量。
     * 调用方：ChatServiceImpl 收到 Python 摘要成功结果后。
     */
    public void saveSummaryCursor(Long sessionId, int summarizedMessageCount) {
        redisTemplate.opsForValue().set(summaryCursorKey(sessionId), String.valueOf(Math.max(0, summarizedMessageCount)));
    }

    private boolean isVisibleSession(ChatSession session, Long userId) {
        return session != null
                && userId.equals(session.getUserId())
                && !SESSION_STATUS_DELETED.equals(session.getStatus());
    }

    private long toEpochMillis(ChatSession session) {
        return session.getLastActive()
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }

    private String sessionKey(Long sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private String userSessionsKey(Long userId) {
        return USER_SESSION_KEY_PREFIX + userId + ":sessions";
    }

    private String messageKey(Long sessionId) {
        return MESSAGE_KEY_PREFIX + sessionId;
    }

    private String longTermMemoryKey(Long sessionId) {
        return LONG_TERM_MEMORY_KEY_PREFIX + sessionId;
    }

    private String keyFactsKey(Long sessionId) {
        return KEY_FACTS_KEY_PREFIX + sessionId;
    }

    private String summaryCursorKey(Long sessionId) {
        return SUMMARY_CURSOR_KEY_PREFIX + sessionId;
    }

    private <T> T readJson(String key, Class<T> targetClass) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return fromJson(value, targetClass);
    }

    private void writeJson(String key, Object value) {
        redisTemplate.opsForValue().set(key, toJson(value));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("聊天数据序列化失败", ex);
        }
    }

    private <T> T fromJson(String value, Class<T> targetClass) {
        try {
            return objectMapper.readValue(value, targetClass);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("聊天数据反序列化失败", ex);
        }
    }

    private Map<String, Object> parseObjectMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of("notes", value);
        }
    }
}
