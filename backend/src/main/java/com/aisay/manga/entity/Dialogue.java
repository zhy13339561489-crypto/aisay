package com.aisay.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("dialogues")
public class Dialogue {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sceneId;

    private Long characterId;

    private String content;

    @TableField("`order`")
    private Integer order;
}
