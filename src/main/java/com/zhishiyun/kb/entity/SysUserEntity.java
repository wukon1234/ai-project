package com.zhishiyun.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 系统用户，对应 sys_user。 */
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
    /** Azure AD oid，用于 SSO 绑定 */
    private String ssoSubject;
}
