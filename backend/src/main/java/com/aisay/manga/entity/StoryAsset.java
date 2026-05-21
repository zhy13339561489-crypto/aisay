package com.aisay.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("story_assets")
public class StoryAsset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storyId;

    private String assetType;

    private String name;

    private String description;

    private String imagePrompt;

    private String imagePath;

    private String audioPath;

    private Long firstSectionId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
