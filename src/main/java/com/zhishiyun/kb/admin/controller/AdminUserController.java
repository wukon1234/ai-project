package com.zhishiyun.kb.admin.controller;

import com.zhishiyun.kb.admin.AdminAuthHelper;
import com.zhishiyun.kb.admin.dto.AdminUserCreateRequest;
import com.zhishiyun.kb.admin.dto.AdminUserRoleRequest;
import com.zhishiyun.kb.admin.service.AdminUserService;
import com.zhishiyun.kb.common.Result;
import com.zhishiyun.kb.model.AuthUser;
import java.util.HashMap;
import java.util.Map;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public Result<?> list(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        // KB_ADMIN 也需只读用户目录，供 ACL 选人；写操作仍要求 SYS_ADMIN
        AdminAuthHelper.requireAdmin();
        return Result.ok(adminUserService.list(status, role, keyword, page, size));
    }

    @PostMapping
    public Result<?> create(@Valid @RequestBody AdminUserCreateRequest request) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminUserService.create(user.getUserId(), request));
    }

    @PatchMapping("/{id}/approve")
    public Result<?> approve(@PathVariable Long id) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminUserService.approve(user.getUserId(), id));
    }

    @PatchMapping("/{id}/reject")
    public Result<?> reject(@PathVariable Long id) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminUserService.reject(user.getUserId(), id));
    }

    @PatchMapping("/{id}/disable")
    public Result<?> disable(@PathVariable Long id) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminUserService.disable(user.getUserId(), id));
    }

    @PatchMapping("/{id}/enable")
    public Result<?> enable(@PathVariable Long id) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminUserService.enable(user.getUserId(), id));
    }

    @PatchMapping("/{id}/role")
    public Result<?> role(@PathVariable Long id, @Valid @RequestBody AdminUserRoleRequest request) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminUserService.changeRole(user.getUserId(), id, request.getRole()));
    }

    @PostMapping("/{id}/reset-password")
    public Result<?> resetPassword(@PathVariable Long id) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        Map<String, String> data = new HashMap<String, String>();
        data.put("message", adminUserService.resetPassword(user.getUserId(), id));
        return Result.ok(data);
    }
}
