package com.zhishiyun.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 知识库，对应 kb_library。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_library")
public class KbLibraryEntity extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String description;
    private String tags;
    private Integer docCount;
    /** 1=全员可读 */
    private Integer publicRead;
}
