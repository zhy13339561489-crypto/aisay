package com.aisay.manga.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryOutlineReviseResponse {

    private String storySummary;

    private String outline;

    private List<StoryOutlineGenerateResponse.MainCharacterSetting> mainCharacters;
}
