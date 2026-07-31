package com.zhishiyun.kb.admin.controller;

import com.zhishiyun.kb.admin.AdminAuthHelper;
import com.zhishiyun.kb.admin.service.AdminModelService;
import com.zhishiyun.kb.common.Result;
import com.zhishiyun.kb.model.AuthUser;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/models")
@RequiredArgsConstructor
public class AdminModelController {

    private final AdminModelService adminModelService;

    @GetMapping
    public Result<?> get() {
        AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminModelService.getMaskedConfig());
    }

    @PutMapping
    public Result<?> save(@RequestBody Map<String, Object> body) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminModelService.save(user.getUserId(), body));
    }

    @PostMapping("/test")
    public Result<?> test(@RequestParam(value = "target", defaultValue = "llm") String target) {
        AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminModelService.testConnection(target));
    }
}
