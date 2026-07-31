package com.zhishiyun.kb.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.model.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminAuthHelperTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireAdmin_allowsKbAdmin() {
        setUser(new AuthUser(1L, "KB_ADMIN"));
        AuthUser user = AdminAuthHelper.requireAdmin();
        assertEquals("KB_ADMIN", user.getRoleCode());
    }

    @Test
    void requireAdmin_rejectsEmployee() {
        setUser(new AuthUser(2L, "EMPLOYEE"));
        BizException ex = assertThrows(BizException.class, AdminAuthHelper::requireAdmin);
        assertEquals(40301, ex.getCode());
    }

    @Test
    void requireSysAdmin_rejectsKbAdmin() {
        setUser(new AuthUser(3L, "KB_ADMIN"));
        BizException ex = assertThrows(BizException.class, AdminAuthHelper::requireSysAdmin);
        assertEquals(40301, ex.getCode());
    }

    @Test
    void isSysAdmin() {
        assertTrue(AdminAuthHelper.isSysAdmin(new AuthUser(1L, "SYS_ADMIN")));
    }

    private void setUser(AuthUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
