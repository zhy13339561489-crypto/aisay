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
@TableName("stories")
public class Story {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String genre;

    private String style;

    private String synopsis;

    private String fullContent;

    private String coverImagePath;

    private String status;

    private Integer viewCount;

    private Integer likeCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
