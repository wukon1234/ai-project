package com.zhishiyun.kb.admin.controller;

import com.zhishiyun.kb.admin.AdminAuthHelper;
import com.zhishiyun.kb.admin.dto.AdminAclCreateRequest;
import com.zhishiyun.kb.admin.dto.AdminLibraryCreateRequest;
import com.zhishiyun.kb.admin.dto.AdminLibraryUpdateRequest;
import com.zhishiyun.kb.admin.dto.AdminPublicReadRequest;
import com.zhishiyun.kb.admin.service.AdminAclService;
import com.zhishiyun.kb.admin.service.AdminLibraryService;
import com.zhishiyun.kb.common.Result;
import com.zhishiyun.kb.model.AuthUser;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 — 知识库与 ACL 接口。
 * <p>需 SYS_ADMIN 或 KB_ADMIN；ACL 主体支持用户（user）与部门（dept），权限固定为 READ。
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminLibraryController {

    private final AdminLibraryService adminLibraryService;
    private final AdminAclService adminAclService;

    /** 知识库列表，可按名称/code 关键字过滤。 */
    @GetMapping("/libraries")
    public Result<?> list(@RequestParam(value = "keyword", required = false) String keyword) {
        AdminAuthHelper.requireAdmin();
        return Result.ok(adminLibraryService.list(keyword));
    }

    /** 创建知识库（code 全局唯一）。 */
    @PostMapping("/libraries")
    public Result<?> create(@Valid @RequestBody AdminLibraryCreateRequest request) {
        AuthUser user = AdminAuthHelper.requireAdmin();
        return Result.ok(adminLibraryService.create(user.getUserId(), request));
    }

    /** 按 code 更新知识库元信息。 */
    @PutMapping("/libraries/{code}")
    public Result<?> update(@PathVariable String code, @Valid @RequestBody AdminLibraryUpdateRequest request) {
        AuthUser user = AdminAuthHelper.requireAdmin();
        return Result.ok(adminLibraryService.update(user.getUserId(), code, request));
    }

    /** 列出指定知识库的 ACL 规则。 */
    @GetMapping("/libraries/{code}/acl")
    public Result<?> listAcl(@PathVariable String code) {
        AdminAuthHelper.requireAdmin();
        return Result.ok(adminAclService.listByLibrary(code));
    }

    /** 为知识库新增一条用户或部门 ACL。 */
    @PostMapping("/libraries/{code}/acl")
    public Result<?> addAcl(@PathVariable String code, @Valid @RequestBody AdminAclCreateRequest request) {
        AuthUser user = AdminAuthHelper.requireAdmin();
        return Result.ok(adminAclService.add(user.getUserId(), code, request));
    }

    /** 开关知识库「全员可读」。 */
    @PutMapping("/libraries/{code}/public-read")
    public Result<?> publicRead(@PathVariable String code, @Valid @RequestBody AdminPublicReadRequest request) {
        AuthUser user = AdminAuthHelper.requireAdmin();
        adminAclService.setPublicRead(user.getUserId(), code, Boolean.TRUE.equals(request.getPublicRead()));
        return Result.ok(null);
    }

    /** 按 ACL 主键删除规则。 */
    @DeleteMapping("/acl/{id}")
    public Result<?> removeAcl(@PathVariable Long id) {
        AuthUser user = AdminAuthHelper.requireAdmin();
        adminAclService.remove(user.getUserId(), id);
        return Result.ok(null);
    }
}
