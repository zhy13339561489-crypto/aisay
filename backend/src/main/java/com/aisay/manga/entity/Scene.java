package com.aisay.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "scenes", autoResultMap = true)
public class Scene {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storyId;

    private Integer sceneNumber;

    private String setting;

    private String description;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> visualElements;
}
