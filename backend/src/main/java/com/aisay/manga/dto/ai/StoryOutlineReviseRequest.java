package com.aisay.manga.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryOutlineReviseRequest {

    private Long userId;

    private Long storyId;

    private String title;

    private String synopsis;

    private String outline;

    private String suggestion;
}
