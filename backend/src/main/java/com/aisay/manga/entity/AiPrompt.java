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
@TableName("ai_prompts")
public class AiPrompt {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String promptKey;

    private String basePromptKey;

    private String promptScope;

    private String matchGenre;

    private String matchStyle;

    private Integer priority;

    private String promptName;

    private String category;

    private String description;

    private String templateContent;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
