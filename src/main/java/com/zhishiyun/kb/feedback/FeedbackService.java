package com.zhishiyun.kb.feedback;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.common.enums.FeedbackIssueType;
import com.zhishiyun.kb.feedback.dto.HelpfulRequest;
import com.zhishiyun.kb.feedback.dto.RatingRequest;
import com.zhishiyun.kb.feedback.dto.UnhelpfulRequest;
import com.zhishiyun.kb.infra.mysql.entity.ChatMessageEntity;
import com.zhishiyun.kb.infra.mysql.entity.ChatSessionEntity;
import com.zhishiyun.kb.infra.mysql.entity.FeedbackRecordEntity;
import com.zhishiyun.kb.infra.mysql.entity.UsageEventEntity;
import com.zhishiyun.kb.infra.mysql.mapper.ChatMessageMapper;
import com.zhishiyun.kb.infra.mysql.mapper.ChatSessionMapper;
import com.zhishiyun.kb.infra.mysql.mapper.FeedbackRecordMapper;
import com.zhishiyun.kb.infra.mysql.mapper.UsageEventMapper;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 问答反馈：有帮助/没帮助/评分，写 feedback 并埋点。 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRecordMapper feedbackRecordMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final UsageEventMapper usageEventMapper;

    @Transactional
    /** 标记有帮助。 */
    public void helpful(Long userId, HelpfulRequest request) {
        ChatMessageEntity message = validateOwnAssistantMessage(userId, request.getMessageId());
        upsertFeedback(userId, message, "HELPFUL", null, null, null);
    }

    @Transactional
    /** 标记没帮助（含问题类型与补充说明）。 */
    public void unhelpful(Long userId, UnhelpfulRequest request) {
        if (Boolean.TRUE.equals(request.getKnowCorrect())
                && (request.getCorrectAnswer() == null || request.getCorrectAnswer().trim().isEmpty())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "knowCorrect=true 时 correctAnswer 必填");
        }
        try {
            FeedbackIssueType.valueOf(request.getIssueType());
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "issueType 非法");
        }
        ChatMessageEntity message = validateOwnAssistantMessage(userId, request.getMessageId());
        upsertFeedback(
                userId,
                message,
                "UNHELPFUL",
                request.getIssueType(),
                request.getComment(),
                request.getCorrectAnswer());
    }

    @Transactional
    /** 会话整体评分。 */
    public void rating(Long userId, RatingRequest request) {
        ChatMessageEntity message = validateOwnAssistantMessage(userId, request.getMessageId());
        upsertFeedback(userId, message, "RATING", null, null, null);
        FeedbackRecordEntity rating = feedbackRecordMapper.selectOne(new LambdaQueryWrapper<FeedbackRecordEntity>()
                .eq(FeedbackRecordEntity::getUserId, userId)
                .eq(FeedbackRecordEntity::getMessageId, message.getId())
                .eq(FeedbackRecordEntity::getType, "RATING")
                .last("limit 1"));
        if (rating != null) {
            rating.setRatingScore(request.getScore());
            feedbackRecordMapper.updateById(rating);
        }
        ChatSessionEntity session = chatSessionMapper.selectById(message.getSessionId());
        if (session != null) {
            session.setRating(new BigDecimal(request.getScore()));
            chatSessionMapper.updateById(session);
        }
    }

    private ChatMessageEntity validateOwnAssistantMessage(Long userId, Long messageId) {
        ChatMessageEntity message = chatMessageMapper.selectById(messageId);
        if (message == null || !"assistant".equals(message.getRole())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "messageId 无效");
        }
        ChatSessionEntity session = chatSessionMapper.selectById(message.getSessionId());
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "无权反馈该消息");
        }
        return message;
    }

    private void upsertFeedback(
            Long userId,
            ChatMessageEntity message,
            String type,
            String issueType,
            String comment,
            String correctAnswer) {
        FeedbackRecordEntity existing = feedbackRecordMapper.selectOne(new LambdaQueryWrapper<FeedbackRecordEntity>()
                .eq(FeedbackRecordEntity::getUserId, userId)
                .eq(FeedbackRecordEntity::getMessageId, message.getId())
                .eq(FeedbackRecordEntity::getType, type)
                .last("limit 1"));
        if (existing == null) {
            existing = new FeedbackRecordEntity();
            existing.setUserId(userId);
            existing.setSessionId(message.getSessionId());
            existing.setMessageId(message.getId());
            existing.setType(type);
            existing.setIssueType(issueType);
            existing.setComment(comment);
            existing.setCorrectAnswer(correctAnswer);
            feedbackRecordMapper.insert(existing);
        } else {
            existing.setIssueType(issueType);
            existing.setComment(comment);
            existing.setCorrectAnswer(correctAnswer);
            feedbackRecordMapper.updateById(existing);
        }
        UsageEventEntity usage = new UsageEventEntity();
        usage.setUserId(userId);
        usage.setEventType("FEEDBACK");
        usage.setRefId(String.valueOf(message.getId()));
        usage.setExtraJson("{\"type\":\"" + type + "\"}");
        usageEventMapper.insert(usage);
    }
}
