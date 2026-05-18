package com.aisay.manga.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryDetailUpdateRequest {

    @Size(max = 5000, message = "Synopsis must not exceed 5000 characters")
    private String synopsis;

    private String fullContent;

    @Valid
    private List<CharacterUpdateItem> characters;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CharacterUpdateItem {

        @Size(max = 100, message = "Character name must not exceed 100 characters")
        private String name;

        @Size(max = 100, message = "Character role must not exceed 100 characters")
        private String role;

        private String description;

        private String personality;

        private Map<String, Object> appearance;
    }
}
