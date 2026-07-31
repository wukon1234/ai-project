package com.zhishiyun.kb.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 使用埋点事件，对应 usage_event。 */
@Data
@TableName("usage_event")
public class UsageEventEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String eventType;
    private String libraryCode;
    private String refId;
    private String extraJson;
    private LocalDateTime eventTime;
}
