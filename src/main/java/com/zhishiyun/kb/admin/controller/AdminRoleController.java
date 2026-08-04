package com.zhishiyun.kb.admin.controller;

import com.zhishiyun.kb.admin.AdminAuthHelper;
import com.zhishiyun.kb.admin.service.AdminRoleService;
import com.zhishiyun.kb.common.Result;
import com.zhishiyun.kb.model.AuthUser;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 — 角色与权限矩阵。
 * <p>仅 SYS_ADMIN；矩阵持久化于 sys_config（key = admin.role.matrix）。
 */
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    /** 角色卡片列表（含人数与权限快照）。 */
    @GetMapping
    public Result<?> list() {
        AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminRoleService.listRoles());
    }

    /** 查询指定角色的权限位。 */
    @GetMapping("/{code}/permissions")
    public Result<?> permissions(@PathVariable String code) {
        AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminRoleService.getPermissions(code));
    }

    /**
     * 保存角色权限；EMPLOYEE 强制无 admin.access，SYS_ADMIN 强制全开。
     */
    @PutMapping("/{code}/permissions")
    public Result<?> save(@PathVariable String code, @RequestBody Map<String, Boolean> body) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminRoleService.savePermissions(user.getUserId(), code, body));
    }
}
