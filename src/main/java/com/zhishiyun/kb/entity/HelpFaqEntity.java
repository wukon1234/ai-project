package com.zhishiyun.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 帮助中心 FAQ 实体，对应 help_faq 表。 */
@Data
@TableName("help_faq")
public class HelpFaqEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 问题标题 */
    private String question;
    /** 答案正文 */
    private String answer;
    /** 语言，如 zh-CN */
    private String locale;
    /** 排序号，越小越靠前 */
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
