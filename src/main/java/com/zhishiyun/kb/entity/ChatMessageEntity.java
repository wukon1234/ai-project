package com.zhishiyun.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 会话消息，对应 chat_message。 */
@Data
@TableName("chat_message")
public class ChatMessageEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    /** 识别/思考过程，供历史会话回显 */
    private String thinkingContent;
    private Integer elapsedMs;
    private String answerStatus;
    private String modelName;
    private Integer promptTokens;
    private Integer completionTokens;
    private LocalDateTime createdAt;
}
