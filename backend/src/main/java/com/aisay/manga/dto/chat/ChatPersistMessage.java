package com.aisay.manga.dto.chat;

import com.aisay.manga.entity.ChatSession;
import com.aisay.manga.entity.Message;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatPersistMessage {

    public static final String TYPE_SESSION_UPSERT = "SESSION_UPSERT";

    public static final String TYPE_MESSAGE_INSERT = "MESSAGE_INSERT";

    private String eventId;

    private String eventType;

    private ChatSession session;

    private Message message;
}
