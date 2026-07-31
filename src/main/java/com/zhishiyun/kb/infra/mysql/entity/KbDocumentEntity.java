package com.zhishiyun.kb.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_document")
public class KbDocumentEntity extends BaseEntity {
    // 文档ID
    private Long documentId;
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long libraryId;
    private String libraryCode;
    private String title;
    private String fileType;
    private String category;
    private String storageKey;
    private Integer pages;
    private String summary;
    private String status;
    private Integer viewCount;
    private Long createdBy;
}
