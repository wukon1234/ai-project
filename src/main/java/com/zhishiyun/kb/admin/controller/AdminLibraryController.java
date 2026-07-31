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

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminLibraryController {

    private final AdminLibraryService adminLibraryService;
    private final AdminAclService adminAclService;

    @GetMapping("/libraries")
    public Result<?> list(@RequestParam(value = "keyword", required = false) String keyword) {
        AdminAuthHelper.requireAdmin();
        return Result.ok(adminLibraryService.list(keyword));
    }

    @PostMapping("/libraries")
    public Result<?> create(@Valid @RequestBody AdminLibraryCreateRequest request) {
        AuthUser user = AdminAuthHelper.requireAdmin();
        return Result.ok(adminLibraryService.create(user.getUserId(), request));
    }

    @PutMapping("/libraries/{code}")
    public Result<?> update(@PathVariable String code, @Valid @RequestBody AdminLibraryUpdateRequest request) {
        AuthUser user = AdminAuthHelper.requireAdmin();
        return Result.ok(adminLibraryService.update(user.getUserId(), code, request));
    }

    @GetMapping("/libraries/{code}/acl")
    public Result<?> listAcl(@PathVariable String code) {
        AdminAuthHelper.requireAdmin();
        return Result.ok(adminAclService.listByLibrary(code));
    }

    @PostMapping("/libraries/{code}/acl")
    public Result<?> addAcl(@PathVariable String code, @Valid @RequestBody AdminAclCreateRequest request) {
        AuthUser user = AdminAuthHelper.requireAdmin();
        return Result.ok(adminAclService.add(user.getUserId(), code, request));
    }

    @PutMapping("/libraries/{code}/public-read")
    public Result<?> publicRead(@PathVariable String code, @Valid @RequestBody AdminPublicReadRequest request) {
        AuthUser user = AdminAuthHelper.requireAdmin();
        adminAclService.setPublicRead(user.getUserId(), code, Boolean.TRUE.equals(request.getPublicRead()));
        return Result.ok(null);
    }

    @DeleteMapping("/acl/{id}")
    public Result<?> removeAcl(@PathVariable Long id) {
        AuthUser user = AdminAuthHelper.requireAdmin();
        adminAclService.remove(user.getUserId(), id);
        return Result.ok(null);
    }
}
