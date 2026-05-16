package com.aisay.manga.repository;

import com.aisay.manga.entity.ChatSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Select("""
            SELECT *
            FROM chat_sessions
            WHERE user_id = #{userId}
              AND status = #{status}
            ORDER BY last_active DESC
            """)
    List<ChatSession> selectByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);
}
