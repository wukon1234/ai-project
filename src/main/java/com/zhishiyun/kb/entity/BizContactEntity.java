package com.zhishiyun.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 业务联系人（如 HR），对应 biz_contact。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_contact")
public class BizContactEntity extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String libraryCode;
    private String name;
    private String title;
    private String wecom;
    private String extNo;
}
