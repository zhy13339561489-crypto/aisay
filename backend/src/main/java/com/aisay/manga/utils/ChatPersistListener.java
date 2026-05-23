package com.aisay.manga.utils;

import com.aisay.manga.config.AiRabbitConstants;
import com.aisay.manga.dto.chat.ChatPersistMessage;
import com.aisay.manga.repository.ChatSessionMapper;
import com.aisay.manga.repository.MessageMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ChatPersistListener {

    private final ChatSessionMapper chatSessionMapper;

    private final MessageMapper messageMapper;

    public ChatPersistListener(ChatSessionMapper chatSessionMapper, MessageMapper messageMapper) {
        this.chatSessionMapper = chatSessionMapper;
        this.messageMapper = messageMapper;
    }

    /**
     * 作用：监听聊天持久化队列，将 Redis 中已写入的会话和消息异步同步到 MySQL。
     * 调用方：RabbitMQ 消费线程收到 aisay.chat.persist 消息时自动调用。
     */
    @RabbitListener(queues = AiRabbitConstants.CHAT_PERSIST_QUEUE)
    @Transactional
    public void handlePersistMessage(ChatPersistMessage message) {
        if (message == null || message.getEventType() == null) {
            return;
        }

        if (ChatPersistMessage.TYPE_SESSION_UPSERT.equals(message.getEventType()) && message.getSession() != null) {
            chatSessionMapper.upsertWithId(message.getSession());
            return;
        }

        if (ChatPersistMessage.TYPE_MESSAGE_INSERT.equals(message.getEventType()) && message.getMessage() != null) {
            messageMapper.insertIgnoreWithId(message.getMessage());
        }
    }
}
