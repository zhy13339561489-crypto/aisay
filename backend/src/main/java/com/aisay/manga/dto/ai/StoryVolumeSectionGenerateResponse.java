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
    }
}
