package com.zhishiyun.kb.admin;

import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.model.AuthUser;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 管理后台角色校验与当前登录用户获取。
 * <p>角色层级：SYS_ADMIN（全部管理能力）&gt; KB_ADMIN（知识相关）&gt; EMPLOYEE（仅用户端）。
 */
public final class AdminAuthHelper {

    /** 系统管理员：用户/角色/模型等全局配置。 */
    public static final String ROLE_SYS_ADMIN = "SYS_ADMIN";
    /** 知识管理员：知识库、入库、ACL、知识相关审计。 */
    public static final String ROLE_KB_ADMIN = "KB_ADMIN";
    /** 普通员工：无管理后台访问权。 */
    public static final String ROLE_EMPLOYEE = "EMPLOYEE";

    private static final Set<String> ADMIN_ROLES = new HashSet<String>(Arrays.asList(ROLE_SYS_ADMIN, ROLE_KB_ADMIN));

    private AdminAuthHelper() {
    }

    /** 要求当前用户具备管理后台权限（SYS_ADMIN 或 KB_ADMIN）。 */
    public static AuthUser requireAdmin() {
        AuthUser user = currentUser();
        if (!ADMIN_ROLES.contains(user.getRoleCode())) {
            throw new BizException(ErrorCode.FORBIDDEN_LIBRARY, "无管理后台权限");
        }
        return user;
    }

    /** 要求当前用户为系统管理员。 */
    public static AuthUser requireSysAdmin() {
        AuthUser user = currentUser();
        if (!ROLE_SYS_ADMIN.equals(user.getRoleCode())) {
            throw new BizException(ErrorCode.FORBIDDEN_LIBRARY, "需要系统管理员权限");
        }
        return user;
    }

    /** 判断是否为系统管理员。 */
    public static boolean isSysAdmin(AuthUser user) {
        return user != null && ROLE_SYS_ADMIN.equals(user.getRoleCode());
    }

    /** 从 SecurityContext 读取当前登录用户；未登录则抛出 UNAUTHORIZED。 */
    public static AuthUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return (AuthUser) auth.getPrincipal();
    }
}
