package com.zhishiyun.kb.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 问答反馈记录，对应 feedback_record。 */
@Data
@TableName("feedback_record")
public class FeedbackRecordEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private Long sessionId;
    private Long userId;
    private String type;
    private String issueType;
    private String comment;
    private String correctAnswer;
    private Integer ratingScore;
    private LocalDateTime createdAt;
}
