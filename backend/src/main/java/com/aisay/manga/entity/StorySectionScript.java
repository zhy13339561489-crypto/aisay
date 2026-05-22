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
@TableName("story_section_scripts")
public class StorySectionScript {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storyId;

    private Long sectionId;

    private Integer shotNumber;

    private Integer durationSeconds;

    private String shotType;

    private String cameraMovement;

    private String action;

    private String dialogue;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
