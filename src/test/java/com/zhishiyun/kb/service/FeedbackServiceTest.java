package com.zhishiyun.kb.service;


import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.dto.UnhelpfulRequest;
import com.zhishiyun.kb.entity.ChatMessageEntity;
import com.zhishiyun.kb.mapper.ChatMessageMapper;
import com.zhishiyun.kb.mapper.ChatSessionMapper;
import com.zhishiyun.kb.mapper.FeedbackRecordMapper;
import com.zhishiyun.kb.mapper.UsageEventMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FeedbackServiceTest {

    @Test
    void shouldValidateCorrectAnswerWhenKnowCorrect() {
        FeedbackRecordMapper feedbackRecordMapper = Mockito.mock(FeedbackRecordMapper.class);
        ChatMessageMapper chatMessageMapper = Mockito.mock(ChatMessageMapper.class);
        ChatSessionMapper chatSessionMapper = Mockito.mock(ChatSessionMapper.class);
        UsageEventMapper usageEventMapper = Mockito.mock(UsageEventMapper.class);
        FeedbackService service = new FeedbackService(feedbackRecordMapper, chatMessageMapper, chatSessionMapper, usageEventMapper);

        UnhelpfulRequest req = new UnhelpfulRequest();
        req.setMessageId(1L);
        req.setIssueType("INACCURATE");
        req.setKnowCorrect(true);
        req.setCorrectAnswer("");

        Assertions.assertThrows(BizException.class, () -> service.unhelpful(1001L, req));
    }

    @Test
    void shouldRejectNonAssistantMessage() {
        FeedbackRecordMapper feedbackRecordMapper = Mockito.mock(FeedbackRecordMapper.class);
        ChatMessageMapper chatMessageMapper = Mockito.mock(ChatMessageMapper.class);
        ChatSessionMapper chatSessionMapper = Mockito.mock(ChatSessionMapper.class);
        UsageEventMapper usageEventMapper = Mockito.mock(UsageEventMapper.class);
        FeedbackService service = new FeedbackService(feedbackRecordMapper, chatMessageMapper, chatSessionMapper, usageEventMapper);

        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setId(1L);
        msg.setRole("user");
        Mockito.when(chatMessageMapper.selectById(1L)).thenReturn(msg);

        UnhelpfulRequest req = new UnhelpfulRequest();
        req.setMessageId(1L);
        req.setIssueType("INACCURATE");
        req.setKnowCorrect(false);

        Assertions.assertThrows(BizException.class, () -> service.unhelpful(1001L, req));
    }
}
