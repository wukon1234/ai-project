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

@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    @GetMapping
    public Result<?> list() {
        AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminRoleService.listRoles());
    }

    @GetMapping("/{code}/permissions")
    public Result<?> permissions(@PathVariable String code) {
        AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminRoleService.getPermissions(code));
    }

    @PutMapping("/{code}/permissions")
    public Result<?> save(@PathVariable String code, @RequestBody Map<String, Boolean> body) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminRoleService.savePermissions(user.getUserId(), code, body));
    }
}
