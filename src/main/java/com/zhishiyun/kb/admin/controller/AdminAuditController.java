package com.zhishiyun.kb.admin.controller;

import com.zhishiyun.kb.admin.AdminAuthHelper;
import com.zhishiyun.kb.admin.service.AdminAuditQueryService;
import com.zhishiyun.kb.common.Result;
import com.zhishiyun.kb.model.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AdminAuditQueryService adminAuditQueryService;

    @GetMapping
    public Result<?> list(
            @RequestParam(value = "range", required = false) String range,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "actions", required = false) String actions,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        AuthUser user = AdminAuthHelper.requireAdmin();
        return Result.ok(adminAuditQueryService.query(user, range, from, to, actor, actions, targetType, keyword, page, size));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(value = "range", required = false) String range,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "actions", required = false) String actions,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "keyword", required = false) String keyword) {
        AuthUser user = AdminAuthHelper.requireAdmin();
        byte[] csv = adminAuditQueryService.exportCsv(user, range, from, to, actor, actions, targetType, keyword);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit.csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(csv);
    }
}
