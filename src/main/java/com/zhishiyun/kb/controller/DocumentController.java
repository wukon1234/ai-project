package com.zhishiyun.kb.controller;


import com.zhishiyun.kb.service.DocumentAiService;
import com.zhishiyun.kb.service.DocumentService;
import com.zhishiyun.kb.service.DocumentAskStreamService;
import com.zhishiyun.kb.model.AuthUser;
import com.zhishiyun.kb.dto.AskRequest;
import com.zhishiyun.kb.common.Result;
import com.zhishiyun.kb.dto.DocumentMetaResponse;
import com.zhishiyun.kb.dto.PageSummaryResponse;
import com.zhishiyun.kb.dto.RelatedChunkResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 文档阅读 API：元数据/文件流/分享/页摘要/相关片段/同文档问答。 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentAiService documentAiService;
    private final DocumentAskStreamService documentAskStreamService;

    /** 文档元数据（含收藏态、浏览埋点）。 */
    @GetMapping("/{id}")
    public Result<DocumentMetaResponse> meta(Authentication auth, @PathVariable Long id) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(documentService.meta(user.getUserId(), id));
    }

    /** 原文预览/下载流。 */
    @GetMapping("/{id}/file")
    public ResponseEntity<InputStreamResource> file(
            Authentication auth,
            @PathVariable Long id,
            @RequestParam(value = "download", defaultValue = "false") boolean download) throws IOException {
        AuthUser user = (AuthUser) auth.getPrincipal();
        File file = documentService.file(user.getUserId(), id, download);
        String disposition = (download ? "attachment" : "inline") + "; filename=\"document-" + id + ".pdf\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentLength(file.length())
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(new FileInputStream(file)));
    }

    /** 浏览埋点（打开原文 / 阅读完成等）。 */
    @PostMapping("/{id}/view")
    public Result<Map<String, Object>> view(
            Authentication auth,
            @PathVariable Long id,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "eventType", defaultValue = "OPEN_SOURCE") String eventType,
            @RequestParam(value = "readMinutes", required = false) Double readMinutes) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        Integer views = documentService.addView(user.getUserId(), id, pageNo, eventType, readMinutes);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("views", views);
        return Result.ok(data);
    }

    /** 生成文档分享短链。 */
    @PostMapping("/{id}/share")
    public Result<Map<String, Object>> share(Authentication auth, @PathVariable Long id) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(documentService.createShare(user.getUserId(), id));
    }

    /** 指定页 AI 摘要。 */
    @GetMapping("/{id}/pages/{pageNo}/summary")
    public Result<PageSummaryResponse> pageSummary(
            Authentication auth,
            @PathVariable Long id,
            @PathVariable Integer pageNo) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(documentAiService.pageSummary(user.getUserId(), id, pageNo));
    }

    /** 同文档相关片段推荐。 */
    @GetMapping("/{id}/related-chunks")
    public Result<List<RelatedChunkResponse>> relatedChunks(
            Authentication auth,
            @PathVariable Long id,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "limit", defaultValue = "5") Integer limit) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(documentAiService.relatedChunks(user.getUserId(), id, pageNo, limit));
    }

    /** 同文档流式问答（SSE）。 */
    @PostMapping(value = "/{id}/ask:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody AskRequest request) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return documentAskStreamService.askStream(user.getUserId(), id, request.getQuestion());
    }
}
