package com.aisay.manga.utils;

import com.aisay.manga.config.AiRabbitConstants;
import com.aisay.manga.dto.ai.AiStoryTaskResultMessage;
import com.aisay.manga.service.impl.StoryServiceImpl;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AiStoryTaskResultListener {

    private final StoryServiceImpl storyService;

    public AiStoryTaskResultListener(StoryServiceImpl storyService) {
        this.storyService = storyService;
    }

    /**
     * 作用：监听 Python 完成后的 AI 任务结果，并交给 StoryServiceImpl 统一落库。
     * 调用方：Spring AMQP 在收到 aisay.ai.story.result 队列消息时自动触发。
     */
    @RabbitListener(queues = AiRabbitConstants.STORY_RESULT_QUEUE)
    public void handleResult(AiStoryTaskResultMessage result) {
        storyService.applyStoryTaskResult(result);
    }
}
