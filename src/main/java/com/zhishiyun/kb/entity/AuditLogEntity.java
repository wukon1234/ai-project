package com.zhishiyun.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 审计日志，对应 audit_log。 */
@Data
@TableName("audit_log")
public class AuditLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private String ip;
    private LocalDateTime createdAt;
}
