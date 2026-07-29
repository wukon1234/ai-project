package com.example.rag.controller;

import com.example.rag.service.PdfParseService;
import com.example.rag.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RagController {

    private final PdfParseService pdfParseService;
    private final RagService ragService;

    public RagController(PdfParseService pdfParseService, RagService ragService) {
        this.pdfParseService = pdfParseService;
        this.ragService = ragService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String fileName = name != null ? name : file.getOriginalFilename();
            log.info("Processing file: {}", fileName);
            
            List<String> pages = pdfParseService.parsePdf(file);
            log.info("Parsed {} pages from {}", pages.size(), fileName);
            
            ragService.addDocuments(pages, fileName);
            
            result.put("success", true);
            result.put("message", "文件处理成功");
            result.put("fileName", fileName);
            result.put("pages", pages.size());
            
        } catch (Exception e) {
            log.error("Upload failed", e);
            result.put("success", false);
            result.put("message", "文件处理失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> query(@RequestBody Map<String, String> request) {
        String question = request.get("question");
        
        if (question == null || question.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "问题不能为空");
            return ResponseEntity.badRequest().body(error);
        }
        
        log.info("Query: {}", question);
        Map<String, Object> result = ragService.query(question);
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> search(
            @RequestBody Map<String, Object> request) {
        
        String question = (String) request.get("question");
        int maxResults = request.containsKey("maxResults") ? 
                ((Number) request.get("maxResults")).intValue() : 5;
        
        List<Map<String, Object>> results = ragService.searchOnly(question, maxResults);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "RAG Demo");
        return ResponseEntity.ok(health);
    }
}
