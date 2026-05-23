package com.aisay.manga.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatStartRequest {

    private Long storyId;

    @Size(max = 200, message = "Session title must not exceed 200 characters")
    private String title;
}
