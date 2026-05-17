package com.aisay.manga.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryOutlineGenerateResponse {

    private String novelName;

    private String storySummary;

    private String outline;

    private List<MainCharacterSetting> mainCharacters;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MainCharacterSetting {

        private String name;

        private String role;

        private String description;

        private String personality;

        private Map<String, Object> appearance;
    }
}
