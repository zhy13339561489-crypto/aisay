package com.aisay.manga.config;

public final class AiRabbitConstants {

    public static final String EXCHANGE = "aisay.ai.exchange";

    public static final String STORY_REQUEST_QUEUE = "aisay.ai.story.request";

    public static final String STORY_RESULT_QUEUE = "aisay.ai.story.result";

    public static final String STORY_REQUEST_ROUTING_KEY = "ai.story.request";

    public static final String STORY_RESULT_ROUTING_KEY = "ai.story.result";

    public static final String TASK_STORY_GENERATE = "STORY_GENERATE";

    public static final String TASK_STORY_REVISE = "STORY_REVISE";

    public static final String TASK_VOLUME_GENERATE = "VOLUME_GENERATE";

    public static final String TASK_VOLUME_REVISE = "VOLUME_REVISE";

    public static final String TASK_VOLUME_STORY_GENERATE = "VOLUME_STORY_GENERATE";

    public static final String TASK_VOLUME_SECTION_GENERATE = "VOLUME_SECTION_GENERATE";

    public static final String TASK_SECTION_ASSET_GENERATE = "SECTION_ASSET_GENERATE";

    private AiRabbitConstants() {
    }
}
