package com.aisay.manga.repository;

import com.aisay.manga.entity.Message;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 作用：按会话 ID 查询消息，并按创建时间和 ID 升序排列。
     * 调用方：ChatServiceImpl#getHistory。
     */
    @Select("""
            SELECT *
            FROM messages
            WHERE session_id = #{sessionId}
            ORDER BY created_at ASC, id ASC
            """)
    List<Message> selectBySessionIdOrderByCreatedAtAsc(@Param("sessionId") Long sessionId);

    /**
     * 作用：使用 Redis 生成的稳定 ID 将消息异步写入 MySQL；重复投递时忽略。
     * 调用方：ChatPersistListener#handlePersistMessage。
     */
    @Insert("""
            INSERT IGNORE INTO messages (
                id, session_id, role, content, metadata, created_at
            )
            VALUES (
                #{id}, #{sessionId}, #{role}, #{content}, '{}', #{createdAt}
            )
            """)
    void insertIgnoreWithId(Message message);
}
