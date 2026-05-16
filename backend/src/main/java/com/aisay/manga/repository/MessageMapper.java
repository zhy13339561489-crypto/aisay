package com.aisay.manga.repository;

import com.aisay.manga.entity.Message;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    @Select("""
            SELECT *
            FROM messages
            WHERE session_id = #{sessionId}
            ORDER BY created_at ASC, id ASC
            """)
    List<Message> selectBySessionIdOrderByCreatedAtAsc(@Param("sessionId") Long sessionId);
}
