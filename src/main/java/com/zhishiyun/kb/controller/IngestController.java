package com.zhishiyun.kb.controller;


import com.zhishiyun.kb.service.IngestService;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.common.Result;
import com.zhishiyun.kb.dto.IngestTaskResponse;
import com.zhishiyun.kb.dto.IngestUploadResponse;
import javax.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 内部入库 API（X-Internal-Api-Key），不对 C 端开放。 */
@Validated
@RestController
@RequestMapping("/api/v1/internal/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final IngestService ingestService;

    @Value("${kb.internal.api-key}")
    private String internalApiKey;

    /** 上传文档触发入库。 */
    @PostMapping("/documents")
    public Result<IngestUploadResponse> upload(
            @RequestHeader("X-Internal-Api-Key") String apiKey,
            @RequestParam("file") MultipartFile file,
            @RequestParam("libraryCode") @NotBlank String libraryCode,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", defaultValue = "manual") String category) {
        validateApiKey(apiKey);
        return Result.ok(ingestService.upload(file, libraryCode, title, category));
    }

    /** 查询入库任务状态。 */
    @GetMapping("/tasks/{taskId}")
    public Result<IngestTaskResponse> task(
            @RequestHeader("X-Internal-Api-Key") String apiKey,
            @PathVariable Long taskId) {
        validateApiKey(apiKey);
        return Result.ok(ingestService.task(taskId));
    }

    /** 对已有文档重新解析并写入向量库。 */
    @PostMapping("/reindex/{docId}")
    public Result<IngestUploadResponse> reindex(
            @RequestHeader("X-Internal-Api-Key") String apiKey,
            @PathVariable Long docId) {
        validateApiKey(apiKey);
        return Result.ok(ingestService.reindex(docId));
    }

    /** 校验内部 API Key。 */
    private void validateApiKey(String apiKey) {
        if (!internalApiKey.equals(apiKey)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }
}
