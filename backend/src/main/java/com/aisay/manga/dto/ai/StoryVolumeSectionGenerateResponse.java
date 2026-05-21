package com.aisay.manga.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryVolumeSectionGenerateResponse {

    private List<VolumeSectionItem> sections;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeSectionItem {

        private Integer sectionNumber;

        private String title;

        private String summary;

        private String content;

        private String endingHook;

        private List<SectionAssetItem> assets;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionAssetItem {

        private String assetType;

        private String name;

        private String description;

        private String imagePrompt;

        private String imagePath;

        private String audioPath;

        private Boolean firstAppearance;

        private String generationError;
    }
}
