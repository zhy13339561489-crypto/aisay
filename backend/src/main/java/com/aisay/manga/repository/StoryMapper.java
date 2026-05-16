package com.aisay.manga.repository;

import com.aisay.manga.entity.Story;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StoryMapper extends BaseMapper<Story> {

    @Select("""
            SELECT *
            FROM stories
            WHERE user_id = #{userId}
            ORDER BY updated_at DESC, id DESC
            """)
    Page<Story> selectPageByUserId(Page<Story> page, @Param("userId") Long userId);
}
