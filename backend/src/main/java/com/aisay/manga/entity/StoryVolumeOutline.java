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
@TableName("story_volume_outlines")
public class StoryVolumeOutline {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storyId;

    private Integer volumeNumber;

    private String title;

    private String summary;

    private String content;

    private String endingHook;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
