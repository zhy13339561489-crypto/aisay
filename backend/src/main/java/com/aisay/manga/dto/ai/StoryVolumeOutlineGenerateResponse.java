package com.aisay.manga.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryVolumeOutlineGenerateResponse {

    private List<VolumeOutlineItem> volumes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeOutlineItem {

        private Integer volumeNumber;

        private String title;

        private String summary;

        private String content;

        private String endingHook;
    }
}
