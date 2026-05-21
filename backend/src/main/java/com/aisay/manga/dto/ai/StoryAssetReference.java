package com.aisay.manga.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryAssetReference {

    private String assetType;

    private String name;

    private String description;

    private String imagePrompt;

    private String imagePath;

    private String audioPath;
}
