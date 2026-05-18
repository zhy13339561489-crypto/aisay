package com.aisay.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "chat_sessions", autoResultMap = true)
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long storyId;

    private String sessionKey;

    private String title;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> contextData;

    private String currentStage;

    private Integer progressPercentage;

    private String status;

    private LocalDateTime startedAt;

    private LocalDateTime lastActive;
}
