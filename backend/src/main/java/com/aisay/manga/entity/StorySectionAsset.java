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
@TableName("story_section_assets")
public class StorySectionAsset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storyId;

    private Long sectionId;

    private Long assetId;

    private LocalDateTime createdAt;
}
