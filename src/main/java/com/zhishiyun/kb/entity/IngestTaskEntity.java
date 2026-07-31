package com.zhishiyun.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 入库异步任务，对应 ingest_task。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ingest_task")
public class IngestTaskEntity extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private String status;
    private Integer progress;
    private String errorMsg;
}
