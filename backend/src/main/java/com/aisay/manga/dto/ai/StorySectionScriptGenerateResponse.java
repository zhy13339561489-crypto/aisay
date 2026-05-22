package com.aisay.manga.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StorySectionScriptGenerateResponse {

    private Integer sectionNumber;

    private Integer totalDurationSeconds;

    private List<ScriptShotItem> shots;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScriptShotItem {

        private Integer shotNumber;

        private Integer durationSeconds;

        private String shotType;

        private String cameraMovement;

        private String action;

        private String dialogue;
    }
}
