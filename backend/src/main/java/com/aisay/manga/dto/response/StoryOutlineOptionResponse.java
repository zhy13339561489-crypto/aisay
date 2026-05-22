package com.aisay.manga.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryOutlineOptionResponse {

    private Long id;

    private String type;

    private String name;

    private String description;

    private Integer sortOrder;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
