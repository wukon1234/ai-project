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

/**
 * 管理后台 — 用户管理接口。
 * <p>列表对 KB_ADMIN 只读开放（供 ACL 选人）；创建/审核/启停/改角色/重置密码仅 SYS_ADMIN。
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 分页查询用户；支持按状态、角色、关键字过滤。 */
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

    /** 创建用户并初始化默认偏好。 */
    @PostMapping
    public Result<?> create(@Valid @RequestBody AdminUserCreateRequest request) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminUserService.create(user.getUserId(), request));
    }

    /** 审核通过：status → 1（启用）。 */
    @PatchMapping("/{id}/approve")
    public Result<?> approve(@PathVariable Long id) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminUserService.approve(user.getUserId(), id));
    }

    /** 审核拒绝：status → 2（禁用）。 */
    @PatchMapping("/{id}/reject")
    public Result<?> reject(@PathVariable Long id) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminUserService.reject(user.getUserId(), id));
    }

    /** 禁用账号；禁止禁用最后一个 SYS_ADMIN。 */
    @PatchMapping("/{id}/disable")
    public Result<?> disable(@PathVariable Long id) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminUserService.disable(user.getUserId(), id));
    }

    /** 启用账号：status → 1。 */
    @PatchMapping("/{id}/enable")
    public Result<?> enable(@PathVariable Long id) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminUserService.enable(user.getUserId(), id));
    }

    /** 调整用户角色；禁止降级最后一个 SYS_ADMIN。 */
    @PatchMapping("/{id}/role")
    public Result<?> role(@PathVariable Long id, @Valid @RequestBody AdminUserRoleRequest request) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminUserService.changeRole(user.getUserId(), id, request.getRole()));
    }

    /** 重置密码并返回临时口令（联调用，未发邮件）。 */
    @PostMapping("/{id}/reset-password")
    public Result<?> resetPassword(@PathVariable Long id) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        Map<String, String> data = new HashMap<String, String>();
        data.put("message", adminUserService.resetPassword(user.getUserId(), id));
        return Result.ok(data);
    }
}
