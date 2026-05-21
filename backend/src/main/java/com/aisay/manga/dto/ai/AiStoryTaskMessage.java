package com.aisay.manga.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiStoryTaskMessage {

    private String taskId;

    private String taskType;

    private Long userId;

    private Long storyId;

    private Long volumeId;

    private String genre;

    private String plot;

    private String title;

    private String storyStyle;

    private String storySummary;

    private String outline;

    private List<StoryOutlineGenerateResponse.MainCharacterSetting> mainCharacters;

    private List<StoryVolumeOutlineGenerateResponse.VolumeOutlineItem> volumeOutlines;

    private StoryVolumeSectionGenerateResponse.VolumeSectionItem section;

    private List<StoryAssetReference> existingAssets;

    private String suggestion;
}
