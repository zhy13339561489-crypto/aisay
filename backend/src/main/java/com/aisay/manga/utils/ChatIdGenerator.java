package com.aisay.manga.utils;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ChatIdGenerator {

    private static final int SEQUENCE_MOD = 100_000;

    private final AtomicInteger sequence = new AtomicInteger();

    /**
     * 作用：生成可直接写入 MySQL BIGINT 主键的聊天数据 ID。
     * 调用方：ChatServiceImpl 创建 Redis 会话和消息时。
     */
    public Long nextId() {
        int nextSequence = Math.floorMod(sequence.getAndIncrement(), SEQUENCE_MOD);
        return System.currentTimeMillis() * SEQUENCE_MOD + nextSequence;
    }
}
