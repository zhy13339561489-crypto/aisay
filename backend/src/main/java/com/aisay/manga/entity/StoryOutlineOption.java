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
@TableName("story_outline_options")
public class StoryOutlineOption {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String optionType;

    private String name;

    private String description;

    private Integer sortOrder;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
