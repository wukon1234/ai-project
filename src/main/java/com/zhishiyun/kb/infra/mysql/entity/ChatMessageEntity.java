package com.zhishiyun.kb.infra.mysql.entity;

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
    private Integer elapsedMs;
    private String answerStatus;
    private String modelName;
    private Integer promptTokens;
    private Integer completionTokens;
    private LocalDateTime createdAt;
}
