package com.aisay.manga.utils;

import com.aisay.manga.config.AiRabbitConstants;
import com.aisay.manga.dto.chat.ChatPersistMessage;
import com.aisay.manga.entity.ChatSession;
import com.aisay.manga.entity.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ChatPersistPublisher {

    private final RabbitTemplate rabbitTemplate;

    public ChatPersistPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 作用：投递聊天会话 upsert 持久化事件，由 RabbitMQ listener 异步写入 MySQL。
     * 调用方：ChatServiceImpl 创建、更新和删除会话时。
     */
    public void publishSessionUpsert(ChatSession session) {
        publish(new ChatPersistMessage(
                UUID.randomUUID().toString(),
                ChatPersistMessage.TYPE_SESSION_UPSERT,
                session,
                null
        ));
    }

    /**
     * 作用：投递聊天消息 insert 持久化事件，由 RabbitMQ listener 异步写入 MySQL。
     * 调用方：ChatServiceImpl 保存用户消息和 AI 回复时。
     */
    public void publishMessageInsert(Message message) {
        publish(new ChatPersistMessage(
                UUID.randomUUID().toString(),
                ChatPersistMessage.TYPE_MESSAGE_INSERT,
                null,
                message
        ));
    }

    private void publish(ChatPersistMessage message) {
        rabbitTemplate.convertAndSend(
                AiRabbitConstants.EXCHANGE,
                AiRabbitConstants.CHAT_PERSIST_ROUTING_KEY,
                message
        );
    }
}
