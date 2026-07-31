package com.zhishiyun.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 问答会话，对应 chat_session。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_session")
public class ChatSessionEntity extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String scope;
    private String shareToken;
    private String lastQuestion;
    private BigDecimal rating;
    private Integer messageCount;
    private Integer deleted;
}
