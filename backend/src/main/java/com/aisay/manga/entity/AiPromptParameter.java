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
@TableName("ai_prompt_parameters")
public class AiPromptParameter {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long promptId;

    private String direction;

    private String paramKey;

    private String paramName;

    private String dataType;

    private Boolean requiredFlag;

    private String description;

    private String exampleValue;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
