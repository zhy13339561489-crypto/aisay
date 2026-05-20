package com.aisay.manga.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StoryDetailResponse extends StoryResponse {

    private String fullContent;

    private List<VolumeOutlineItem> volumeOutlines;

    private List<CharacterItem> characters;

    private List<SceneItem> scenes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CharacterItem {

        private Long id;

        private String name;

        private String role;

        private String description;

        private String personality;

        private Map<String, Object> appearance;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SceneItem {

        private Long id;

        private Integer sceneNumber;

        private String setting;

        private String description;

        private Map<String, Object> visualElements;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeOutlineItem {

        private Long id;

        private Integer volumeNumber;

        private String title;

        private String summary;

        private String content;

        private String endingHook;

        private String detailedContent;
    }
}
