package com.aisay.manga.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiStoryTaskResultMessage {

    private String taskId;

    private String taskType;

    private Long userId;

    private Long storyId;

    private Long volumeId;

    private Integer volumeNumber;

    private Boolean success;

    private String errorMessage;

    private StoryOutlineGenerateResponse storyOutline;

    private StoryVolumeOutlineGenerateResponse volumeOutline;

    private String volumeStory;

    private Boolean partial;

    private Boolean completed;
}
