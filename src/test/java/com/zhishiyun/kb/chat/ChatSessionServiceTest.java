package com.zhishiyun.kb.chat;

import com.zhishiyun.kb.infra.mysql.entity.ChatSessionEntity;
import com.zhishiyun.kb.infra.mysql.mapper.ChatCitationMapper;
import com.zhishiyun.kb.infra.mysql.mapper.ChatMessageMapper;
import com.zhishiyun.kb.infra.mysql.mapper.ChatSessionMapper;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ChatSessionServiceTest {

    @Test
    void shouldCreateSession() {
        ChatSessionMapper sessionMapper = Mockito.mock(ChatSessionMapper.class);
        ChatMessageMapper messageMapper = Mockito.mock(ChatMessageMapper.class);
        ChatCitationMapper citationMapper = Mockito.mock(ChatCitationMapper.class);
        ChatSessionService service = new ChatSessionService(sessionMapper, messageMapper, citationMapper);

        ChatSessionEntity entity = service.create(1001L, "hr");
        Assertions.assertEquals("hr", entity.getScope());
        Mockito.verify(sessionMapper).insert(Mockito.any(ChatSessionEntity.class));
    }

    @Test
    void shouldCallBatchDelete() {
        ChatSessionMapper sessionMapper = Mockito.mock(ChatSessionMapper.class);
        ChatMessageMapper messageMapper = Mockito.mock(ChatMessageMapper.class);
        ChatCitationMapper citationMapper = Mockito.mock(ChatCitationMapper.class);
        ChatSessionService service = Mockito.spy(new ChatSessionService(sessionMapper, messageMapper, citationMapper));
        Mockito.doNothing().when(service).delete(Mockito.eq(1001L), Mockito.anyLong());

        service.batchDelete(1001L, Arrays.asList(1L, 2L, 3L));

        Mockito.verify(service, Mockito.times(3)).delete(Mockito.eq(1001L), Mockito.anyLong());
    }
}
