package com.zhishiyun.kb.admin.controller;

import com.zhishiyun.kb.admin.AdminAuthHelper;
import com.zhishiyun.kb.admin.service.AdminDashboardService;
import com.zhishiyun.kb.admin.service.AdminIngestService;
import com.zhishiyun.kb.common.Result;
import com.zhishiyun.kb.model.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminIngestController {

    private final AdminIngestService adminIngestService;
    private final AdminDashboardService adminDashboardService;

    @GetMapping("/dashboard")
    public Result<?> dashboard() {
        AuthUser user = AdminAuthHelper.requireAdmin();
        return Result.ok(adminDashboardService.overview(user));
    }

    @PostMapping("/ingest/documents")
    public Result<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("libraryCode") String libraryCode,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", defaultValue = "manual") String category) {
        AuthUser user = AdminAuthHelper.requireAdmin();
        return Result.ok(adminIngestService.upload(user.getUserId(), file, libraryCode, title, category));
    }

    @GetMapping("/ingest/tasks")
    public Result<?> tasks(
            @RequestParam(value = "libraryCode", required = false) String libraryCode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        AdminAuthHelper.requireAdmin();
        return Result.ok(adminIngestService.list(libraryCode, status, keyword, page, size));
    }

    @GetMapping("/ingest/tasks/{id}")
    public Result<?> task(@PathVariable Long id) {
        AdminAuthHelper.requireAdmin();
        return Result.ok(adminIngestService.task(id));
    }

    @PostMapping("/ingest/tasks/{id}/retry")
    public Result<?> retry(@PathVariable Long id) {
        AuthUser user = AdminAuthHelper.requireAdmin();
        return Result.ok(adminIngestService.retry(user.getUserId(), id));
    }

    @PostMapping("/ingest/reindex/{docId}")
    public Result<?> reindex(@PathVariable Long docId) {
        AuthUser user = AdminAuthHelper.requireAdmin();
        return Result.ok(adminIngestService.reindex(user.getUserId(), docId));
    }
}
