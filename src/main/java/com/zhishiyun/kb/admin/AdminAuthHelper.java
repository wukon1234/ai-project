package com.zhishiyun.kb.admin;

import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.model.AuthUser;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 管理后台角色校验与当前用户获取。 */
public final class AdminAuthHelper {

    public static final String ROLE_SYS_ADMIN = "SYS_ADMIN";
    public static final String ROLE_KB_ADMIN = "KB_ADMIN";
    public static final String ROLE_EMPLOYEE = "EMPLOYEE";

    private static final Set<String> ADMIN_ROLES = new HashSet<String>(Arrays.asList(ROLE_SYS_ADMIN, ROLE_KB_ADMIN));

    private AdminAuthHelper() {
    }

    public static AuthUser requireAdmin() {
        AuthUser user = currentUser();
        if (!ADMIN_ROLES.contains(user.getRoleCode())) {
            throw new BizException(ErrorCode.FORBIDDEN_LIBRARY, "无管理后台权限");
        }
        return user;
    }

    public static AuthUser requireSysAdmin() {
        AuthUser user = currentUser();
        if (!ROLE_SYS_ADMIN.equals(user.getRoleCode())) {
            throw new BizException(ErrorCode.FORBIDDEN_LIBRARY, "需要系统管理员权限");
        }
        return user;
    }

    public static boolean isSysAdmin(AuthUser user) {
        return user != null && ROLE_SYS_ADMIN.equals(user.getRoleCode());
    }

    public static AuthUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return (AuthUser) auth.getPrincipal();
    }
}
