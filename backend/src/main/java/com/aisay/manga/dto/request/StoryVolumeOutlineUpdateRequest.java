package com.aisay.manga.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryVolumeOutlineUpdateRequest {

    @Valid
    @NotEmpty(message = "Volume outline list is required")
    private List<VolumeOutlineUpdateItem> volumes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeOutlineUpdateItem {

        @NotNull(message = "Volume number is required")
        private Integer volumeNumber;

        @NotBlank(message = "Volume title is required")
        @Size(max = 200, message = "Volume title must be at most 200 characters")
        private String title;

        @Size(max = 2000, message = "Volume summary must be at most 2000 characters")
        private String summary;

        @Size(max = 20000, message = "Volume content must be at most 20000 characters")
        private String content;

        @Size(max = 2000, message = "Ending hook must be at most 2000 characters")
        private String endingHook;
    }
}
