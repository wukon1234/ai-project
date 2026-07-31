package com.zhishiyun.kb.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 页面 Vision/OCR 增强结果，对应 kb_page_vision。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_page_vision")
public class KbPageVisionEntity extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private Integer pageNo;
    private Integer needVision;
    private String visionStatus;
    private String visionText;
    private String visionSummary;
}
