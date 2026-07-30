package com.zhishiyun.kb.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUserEntity extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String empNo;
    private String name;
    private String email;
    private String mobile;
    private String passwordHash;
    private String deptName;
    private String roleCode;
    private Integer status;
}
