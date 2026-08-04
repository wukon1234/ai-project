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

/**
 * 管理后台 — LLM / Embedding / OCR / Vision / RAG 模型配置。
 * <p>仅 SYS_ADMIN；对外返回的 apiKey 已掩码。
 */
@RestController
@RequestMapping("/api/v1/admin/models")
@RequiredArgsConstructor
public class AdminModelController {

    private final AdminModelService adminModelService;

    /** 读取合并后的模型配置（apiKey 掩码）。 */
    @GetMapping
    public Result<?> get() {
        AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminModelService.getMaskedConfig());
    }

    /** 保存模型配置；含 **** 的 apiKey 视为未改动。 */
    @PutMapping
    public Result<?> save(@RequestBody Map<String, Object> body) {
        AuthUser user = AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminModelService.save(user.getUserId(), body));
    }

    /**
     * 探测指定配置段的 baseUrl 连通性。
     * @param target llm | embedding | ocr | vision 等配置段名
     */
    @PostMapping("/test")
    public Result<?> test(@RequestParam(value = "target", defaultValue = "llm") String target) {
        AdminAuthHelper.requireSysAdmin();
        return Result.ok(adminModelService.testConnection(target));
    }
}
