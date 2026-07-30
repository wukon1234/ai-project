package com.zhishiyun.kb.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("kb_chunk")
public class KbChunkEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private Long libraryId;
    private String libraryCode;
    private Integer pageNo;
    private Integer chunkIndex;
    private String content;
    private Integer tokenEst;
    private String milvusPk;
    private LocalDateTime createdAt;
}
