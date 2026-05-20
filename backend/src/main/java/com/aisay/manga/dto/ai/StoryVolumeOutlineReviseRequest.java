package com.aisay.manga.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryVolumeOutlineReviseRequest {

    private Long userId;

    private Long storyId;

    private String title;

    private String storySummary;

    private String outline;

    private List<StoryOutlineGenerateResponse.MainCharacterSetting> mainCharacters;

    private List<StoryVolumeOutlineGenerateResponse.VolumeOutlineItem> volumeOutlines;

    private String suggestion;
}
