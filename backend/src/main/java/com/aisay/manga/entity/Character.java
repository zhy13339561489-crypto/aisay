package com.aisay.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.Alias;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Alias("MangaCharacter")
@TableName(value = "characters", autoResultMap = true)
public class Character {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storyId;

    private String name;

    private String role;

    private String description;

    private String personality;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> appearance;
}
