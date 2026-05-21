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
@TableName("story_volume_sections")
public class StoryVolumeSection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storyId;

    private Long volumeId;

    private Integer sectionNumber;

    private String title;

    private String summary;

    private String content;

    private String endingHook;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
