package com.zhishiyun.kb.document;

import com.zhishiyun.kb.auth.AuthUser;
import com.zhishiyun.kb.common.Result;
import com.zhishiyun.kb.document.dto.DocumentMetaResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping("/{id}")
    public Result<DocumentMetaResponse> meta(Authentication auth, @PathVariable Long id) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(documentService.meta(user.getUserId(), id));
    }

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

    @PostMapping("/{id}/view")
    public Result<Map<String, Object>> view(
            Authentication auth,
            @PathVariable Long id,
            @RequestParam(value = "pageNo", required = false) Integer pageNo) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        Integer views = documentService.addView(user.getUserId(), id, pageNo);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("views", views);
        return Result.ok(data);
    }

    @PostMapping("/{id}/share")
    public Result<Map<String, Object>> share(Authentication auth, @PathVariable Long id) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        String shareUrl = documentService.share(user.getUserId(), id);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("shareUrl", shareUrl);
        return Result.ok(data);
    }
}
