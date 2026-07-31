package com.zhishiyun.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 用户-知识库访问控制，对应 kb_acl。 */
@Data
@TableName("kb_acl")
public class KbAclEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String deptCode;
    private Long libraryId;
    private String libraryCode;
    private String perm;
    private LocalDateTime createdAt;
}
