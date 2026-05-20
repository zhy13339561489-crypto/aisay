package com.aisay.manga.utils;

import com.aisay.manga.config.AiRabbitConstants;
import com.aisay.manga.dto.ai.AiStoryTaskMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class AiStoryTaskPublisher {

    private final RabbitTemplate rabbitTemplate;

    public AiStoryTaskPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 作用：把非对话类 AI 任务投递到 RabbitMQ，由 Python 后台 worker 异步消费。
     * 调用方：StoryServiceImpl 中的剧情大纲和分卷大纲生成/修改入口。
     */
    public void publish(AiStoryTaskMessage message) {
        rabbitTemplate.convertAndSend(
                AiRabbitConstants.EXCHANGE,
                AiRabbitConstants.STORY_REQUEST_ROUTING_KEY,
                message
        );
    }
}
