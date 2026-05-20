package com.aisay.manga.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryVolumeOutlineReviseRequest {

    @NotBlank(message = "Modification suggestion is required")
    @Size(max = 5000, message = "Modification suggestion must be at most 5000 characters")
    private String suggestion;
}
