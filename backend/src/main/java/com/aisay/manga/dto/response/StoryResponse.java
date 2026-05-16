package com.aisay.manga.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryResponse {

    private Long id;

    private String title;

    private String genre;

    private String style;

    private String synopsis;

    private String status;

    private String coverImagePath;

    private LocalDateTime createdAt;
}
