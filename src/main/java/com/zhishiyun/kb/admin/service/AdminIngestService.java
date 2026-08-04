package com.zhishiyun.kb.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.admin.dto.AdminIngestTaskRecord;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.common.PageResult;
import com.zhishiyun.kb.dto.IngestUploadResponse;
import com.zhishiyun.kb.entity.IngestTaskEntity;
import com.zhishiyun.kb.entity.KbDocumentEntity;
import com.zhishiyun.kb.entity.KbLibraryEntity;
import com.zhishiyun.kb.mapper.IngestTaskMapper;
import com.zhishiyun.kb.mapper.KbDocumentMapper;
import com.zhishiyun.kb.mapper.KbLibraryMapper;
import com.zhishiyun.kb.service.AuditService;
import com.zhishiyun.kb.service.IngestService;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理后台入库编排：上传、任务查询、失败重试、文档重建索引；实际处理委托 {@link IngestService}。
 */
@Service
@RequiredArgsConstructor
public class AdminIngestService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IngestService ingestService;
    private final IngestTaskMapper ingestTaskMapper;
    private final KbDocumentMapper kbDocumentMapper;
    private final KbLibraryMapper kbLibraryMapper;
    private final AuditService auditService;

    /** 上传文件并触发入库，同时写审计。 */
    @Transactional
    public IngestUploadResponse upload(Long actorId, MultipartFile file, String libraryCode, String title, String category) {
        IngestUploadResponse resp = ingestService.upload(file, libraryCode, title, category);
        auditService.write(actorId, "INGEST_UPLOAD", "document", String.valueOf(resp.getDocId()),
                "上传入库 " + (title == null ? file.getOriginalFilename() : title));
        return resp;
    }

    public AdminIngestTaskRecord task(Long taskId) {
        IngestTaskEntity task = ingestTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "task 不存在");
        }
        return toRecord(task);
    }

    /** 入库任务列表；库/状态/标题关键字在内存中过滤后分页。 */
    public PageResult<AdminIngestTaskRecord> list(String libraryCode, String status, String keyword, int page, int size) {
        int pageNum = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        List<IngestTaskEntity> all = ingestTaskMapper.selectList(new LambdaQueryWrapper<IngestTaskEntity>()
                .orderByDesc(IngestTaskEntity::getCreatedAt));
        List<AdminIngestTaskRecord> records = new ArrayList<AdminIngestTaskRecord>();
        for (IngestTaskEntity t : all) {
            AdminIngestTaskRecord r = toRecord(t);
            if (StringUtils.hasText(libraryCode) && !libraryCode.equals(r.getLibraryCode())) {
                continue;
            }
            if (StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status) && !status.equalsIgnoreCase(r.getStatus())) {
                continue;
            }
            if (StringUtils.hasText(keyword)
                    && (r.getTitle() == null || !r.getTitle().toLowerCase().contains(keyword.toLowerCase()))) {
                continue;
            }
            records.add(r);
        }
        long total = records.size();
        int from = Math.min((pageNum - 1) * pageSize, records.size());
        int to = Math.min(from + pageSize, records.size());
        return new PageResult<AdminIngestTaskRecord>(total, pageNum, pageSize, records.subList(from, to));
    }

    /** 仪表盘用：最近 N 条入库任务。 */
    public List<AdminIngestTaskRecord> recent(int limit) {
        List<IngestTaskEntity> tasks = ingestTaskMapper.selectList(new LambdaQueryWrapper<IngestTaskEntity>()
                .orderByDesc(IngestTaskEntity::getCreatedAt)
                .last("limit " + Math.max(1, limit)));
        return tasks.stream().map(this::toRecord).collect(Collectors.toList());
    }

    /** 仅 FAILED 任务可重试，本质为对该文档再次 reindex。 */
    @Transactional
    public IngestUploadResponse retry(Long actorId, Long taskId) {
        IngestTaskEntity task = ingestTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "task 不存在");
        }
        if (!"FAILED".equals(task.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅失败任务可重试");
        }
        IngestUploadResponse resp = ingestService.reindex(task.getDocId());
        auditService.write(actorId, "INGEST_RETRY", "document", String.valueOf(task.getDocId()), "重试入库任务 " + taskId);
        return resp;
    }

    @Transactional
    public IngestUploadResponse reindex(Long actorId, Long docId) {
        IngestUploadResponse resp = ingestService.reindex(docId);
        auditService.write(actorId, "INGEST_REINDEX", "document", String.valueOf(docId), "重建向量");
        return resp;
    }

    private AdminIngestTaskRecord toRecord(IngestTaskEntity task) {
        KbDocumentEntity doc = kbDocumentMapper.selectById(task.getDocId());
        String libraryName = null;
        String libraryCode = null;
        String title = null;
        String fileType = null;
        String category = null;
        Integer pages = null;
        String summary = null;
        if (doc != null) {
            libraryCode = doc.getLibraryCode();
            title = doc.getTitle();
            fileType = doc.getFileType();
            category = doc.getCategory();
            pages = doc.getPages();
            summary = doc.getSummary();
            KbLibraryEntity lib = kbLibraryMapper.selectOne(new LambdaQueryWrapper<KbLibraryEntity>()
                    .eq(KbLibraryEntity::getCode, doc.getLibraryCode())
                    .last("limit 1"));
            if (lib != null) {
                libraryName = lib.getName();
            }
        }
        return AdminIngestTaskRecord.builder()
                .id(String.valueOf(task.getId()))
                .docId(String.valueOf(task.getDocId()))
                .title(title)
                .libraryCode(libraryCode)
                .libraryName(libraryName)
                .fileType(fileType)
                .category(category)
                .status(task.getStatus())
                .progress(task.getProgress())
                .errorMsg(task.getErrorMsg())
                .createdAt(task.getCreatedAt() == null ? null : task.getCreatedAt().format(FMT))
                .pages(pages)
                .summary(summary)
                .build();
    }
}
