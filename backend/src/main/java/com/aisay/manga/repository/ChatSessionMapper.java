package com.aisay.manga.repository;

import com.aisay.manga.entity.ChatSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 作用：按用户和状态查询聊天会话，并按最近活跃时间倒序排列。
     * 调用方：当前保留给会话列表按状态筛选场景，现有 ChatServiceImpl 使用 LambdaQueryWrapper 查询。
     */
    @Select("""
            SELECT *
            FROM chat_sessions
            WHERE user_id = #{userId}
              AND status = #{status}
            ORDER BY last_active DESC
            """)
    List<ChatSession> selectByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);
}
