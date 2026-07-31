package com.zhishiyun.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 系统配置，对应 sys_config。 */
@Data
@TableName("sys_config")
public class SysConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configKey;
    private String configValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
