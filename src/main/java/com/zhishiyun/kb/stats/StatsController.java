package com.zhishiyun.kb.stats;

import com.zhishiyun.kb.auth.AuthUser;
import com.zhishiyun.kb.common.Result;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 使用统计 API。 */
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** 使用统计概览（KPI / 趋势 / 分布）。 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(
            Authentication auth,
            @RequestParam(value = "range", defaultValue = "30d") String range,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(statsService.overview(user.getUserId(), range, from, to));
    }

    /** 导出统计 CSV（UTF-8 BOM，便于 Excel 打开）。 */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            Authentication auth,
            @RequestParam(value = "range", defaultValue = "30d") String range,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        byte[] csv = statsService.exportCsv(user.getUserId(), range, from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"usage-stats.csv\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(csv);
    }
}
