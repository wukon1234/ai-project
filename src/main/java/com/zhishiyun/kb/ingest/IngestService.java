package com.zhishiyun.kb.ingest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.common.enums.DocStatus;
import com.zhishiyun.kb.common.enums.FileType;
import com.zhishiyun.kb.ingest.dto.IngestTaskResponse;
import com.zhishiyun.kb.ingest.dto.IngestUploadResponse;
import com.zhishiyun.kb.infra.mysql.entity.IngestTaskEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbChunkEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbDocumentEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbLibraryEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbPageVisionEntity;
import com.zhishiyun.kb.infra.mysql.mapper.IngestTaskMapper;
import com.zhishiyun.kb.infra.mysql.mapper.KbChunkMapper;
import com.zhishiyun.kb.infra.mysql.mapper.KbDocumentMapper;
import com.zhishiyun.kb.infra.mysql.mapper.KbLibraryMapper;
import com.zhishiyun.kb.infra.mysql.mapper.KbPageVisionMapper;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.util.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestService {

    private final KbLibraryMapper kbLibraryMapper;
    private final KbDocumentMapper kbDocumentMapper;
    private final KbChunkMapper kbChunkMapper;
    private final IngestTaskMapper ingestTaskMapper;
    private final LocalStorageService localStorageService;
    private final ChunkerService chunkerService;
    private final StringRedisTemplate redisTemplate;
    private final JavaOcrService javaOcrService;
    private final KbPageVisionMapper kbPageVisionMapper;

    @Transactional
    public IngestUploadResponse upload(MultipartFile file, String libraryCode, String title, String category) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件不能为空");
        }
        String name = String.valueOf(file.getOriginalFilename()).toLowerCase();
        if (!(name.endsWith(".pdf") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅支持 PDF/图片");
        }
        KbLibraryEntity library = kbLibraryMapper.selectOne(new LambdaQueryWrapper<KbLibraryEntity>()
                .eq(KbLibraryEntity::getCode, libraryCode)
                .last("limit 1"));
        if (library == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "libraryCode 无效");
        }

        String storageKey = localStorageService.save(file);
        KbDocumentEntity document = new KbDocumentEntity();
        document.setLibraryId(library.getId());
        document.setLibraryCode(library.getCode());
        document.setTitle((title == null || title.trim().isEmpty()) ? file.getOriginalFilename() : title);
        document.setFileType(resolveFileType(name));
        document.setCategory(category);
        document.setStorageKey(storageKey);
        document.setStatus(DocStatus.PARSING.name());
        document.setPages(0);
        document.setViewCount(0);
        kbDocumentMapper.insert(document);

        IngestTaskEntity task = new IngestTaskEntity();
        task.setDocId(document.getId());
        task.setStatus("PENDING");
        task.setProgress(0);
        ingestTaskMapper.insert(task);
        parseAsync(task.getId(), document.getId());
        return new IngestUploadResponse(document.getId(), task.getId());
    }

    public IngestTaskResponse task(Long taskId) {
        IngestTaskEntity task = ingestTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "task 不存在");
        }
        return new IngestTaskResponse(task.getStatus(), task.getProgress(), task.getErrorMsg());
    }

    @Transactional
    public IngestUploadResponse reindex(Long docId) {
        KbDocumentEntity document = kbDocumentMapper.selectById(docId);
        if (document == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "doc 不存在");
        }
        kbChunkMapper.delete(new LambdaQueryWrapper<KbChunkEntity>().eq(KbChunkEntity::getDocId, docId));
        document.setStatus(DocStatus.PARSING.name());
        kbDocumentMapper.updateById(document);
        IngestTaskEntity task = new IngestTaskEntity();
        task.setDocId(docId);
        task.setStatus("PENDING");
        task.setProgress(0);
        ingestTaskMapper.insert(task);
        parseAsync(task.getId(), docId);
        return new IngestUploadResponse(docId, task.getId());
    }

    @Async("ingestExecutor")
    @Transactional
    public void parseAsync(Long taskId, Long docId) {
        IngestTaskEntity task = ingestTaskMapper.selectById(taskId);
        KbDocumentEntity document = kbDocumentMapper.selectById(docId);
        try {
            updateTask(task, "RUNNING", 5, null);
            File file = localStorageService.getFile(document.getStorageKey());
            List<PageText> pages = "image".equals(document.getFileType()) ? extractImage(document.getId(), file) : extractPdf(document.getId(), file, task);
            updateTask(task, "RUNNING", 75, null);
            List<KbChunkEntity> chunks = buildChunks(document, pages);
            for (KbChunkEntity chunk : chunks) {
                kbChunkMapper.insert(chunk);
                chunk.setMilvusPk(String.valueOf(chunk.getId()));
                kbChunkMapper.updateById(chunk);
            }
            document.setPages(pages.size());
            document.setSummary(pages.isEmpty() ? "" : truncate(pages.get(0).getText(), 200));
            document.setStatus(DocStatus.READY.name());
            kbDocumentMapper.updateById(document);
            redisTemplate.delete("doc:meta:" + docId);
            updateTask(task, "SUCCESS", 100, null);
        } catch (Exception e) {
            log.error("parse failed, doc={}", docId, e);
            document.setStatus(DocStatus.FAILED.name());
            kbDocumentMapper.updateById(document);
            updateTask(task, "FAILED", task.getProgress(), e.getMessage());
        }
    }

    private List<PageText> extractPdf(Long docId, File pdf, IngestTaskEntity task) throws IOException {
        List<PageText> pages = new ArrayList<PageText>();
        try (PDDocument document = PDDocument.load(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            int total = document.getNumberOfPages();
            int processed = 0;
            for (int i = 1; i <= total; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = normalize(stripper.getText(document));
                Double confidence = null;
                if (!StringUtils.hasText(text) || text.length() < 20) {
                    BufferedImage image = renderer.renderImageWithDPI(i - 1, 172);
                    JavaOcrService.OcrResult ocr = javaOcrService.recognize(image);
                    text = normalize(ocr.getText());
                    confidence = ocr.getConfidence();
                }
                if (confidence != null && confidence < 0.75) {
                    saveVisionMark(docId, pdf.getName(), i, text, confidence);
                }
                pages.add(new PageText(i, text));
                processed++;
                int progress = Math.min(70, 5 + (processed * 65 / Math.max(1, total)));
                updateTask(task, "RUNNING", progress, null);
            }
        }
        return pages;
    }

    private List<PageText> extractImage(Long docId, File imageFile) throws IOException {
        List<PageText> pages = new ArrayList<PageText>();
        BufferedImage image = ImageIO.read(imageFile);
        JavaOcrService.OcrResult ocr = javaOcrService.recognize(image);
        String text = normalize(ocr.getText());
        if (ocr.getConfidence() != null && ocr.getConfidence() < 0.75) {
            saveVisionMark(docId, imageFile.getName(), 1, text, ocr.getConfidence());
        }
        pages.add(new PageText(1, text));
        return pages;
    }

    private List<KbChunkEntity> buildChunks(KbDocumentEntity document, List<PageText> pages) {
        List<KbChunkEntity> result = new ArrayList<KbChunkEntity>();
        int idx = 0;
        for (PageText page : pages) {
            List<String> chunks = chunkerService.split(page.getText(), 500, 100);
            for (String content : chunks) {
                if (content.isEmpty()) {
                    continue;
                }
                KbChunkEntity entity = new KbChunkEntity();
                entity.setDocId(document.getId());
                entity.setLibraryId(document.getLibraryId());
                entity.setLibraryCode(document.getLibraryCode());
                entity.setPageNo(page.getPageNo());
                entity.setChunkIndex(idx++);
                entity.setContent(content);
                entity.setTokenEst(Math.max(1, content.length() / 2));
                result.add(entity);
            }
        }
        return result;
    }

    private void updateTask(IngestTaskEntity task, String status, Integer progress, String err) {
        task.setStatus(status);
        task.setProgress(progress);
        task.setErrorMsg(err);
        ingestTaskMapper.updateById(task);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String resolveFileType(String name) {
        if (name.endsWith(".pdf")) {
            return FileType.pdf.name();
        }
        return FileType.image.name();
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace("\r", "").trim();
    }

    private void saveVisionMark(Long docId, String fileName, int pageNo, String text, double confidence) {
        KbPageVisionEntity v = kbPageVisionMapper.selectOne(new LambdaQueryWrapper<KbPageVisionEntity>()
                .eq(KbPageVisionEntity::getDocId, docId)
                .eq(KbPageVisionEntity::getPageNo, pageNo)
                .last("limit 1"));
        if (v == null) {
            v = new KbPageVisionEntity();
            v.setDocId(docId);
            v.setPageNo(pageNo);
            v.setNeedVision(1);
            v.setVisionStatus("DONE");
            v.setVisionText(truncate(text, 2000));
            v.setVisionSummary("low_confidence=" + confidence + ", file=" + fileName);
            kbPageVisionMapper.insert(v);
        } else {
            v.setNeedVision(1);
            v.setVisionStatus("DONE");
            v.setVisionText(truncate(text, 2000));
            v.setVisionSummary("low_confidence=" + confidence + ", file=" + fileName);
            kbPageVisionMapper.updateById(v);
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class PageText {
        private Integer pageNo;
        private String text;
    }
}
