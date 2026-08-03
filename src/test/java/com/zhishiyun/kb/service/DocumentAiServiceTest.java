package com.zhishiyun.kb.service;

import com.zhishiyun.kb.dto.PageSummaryResponse;
import com.zhishiyun.kb.entity.KbChunkEntity;
import com.zhishiyun.kb.entity.KbDocumentEntity;
import com.zhishiyun.kb.entity.KbLibraryEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DocumentAiServiceTest {

    @Test
    void shouldBuildShortExtractiveSummary() {
        DocumentAiService service = new DocumentAiService(
                Mockito.mock(DocumentService.class),
                Mockito.mock(com.zhishiyun.kb.mapper.KbChunkMapper.class),
                Mockito.mock(com.zhishiyun.kb.mapper.KbLibraryMapper.class),
                Mockito.mock(VectorSearchService.class),
                new com.fasterxml.jackson.databind.ObjectMapper());
        String summary = service.buildExtractiveSummary("员工入职满一年可享受年假。详情见手册第二十三页。");
        Assertions.assertTrue(summary.contains("年假"));
        Assertions.assertTrue(summary.length() <= 200);
    }

    @Test
    void shouldTruncateLongSummary() {
        DocumentAiService service = new DocumentAiService(
                Mockito.mock(DocumentService.class),
                Mockito.mock(com.zhishiyun.kb.mapper.KbChunkMapper.class),
                Mockito.mock(com.zhishiyun.kb.mapper.KbLibraryMapper.class),
                Mockito.mock(VectorSearchService.class),
                new com.fasterxml.jackson.databind.ObjectMapper());
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longText.append("字");
        }
        String summary = service.buildExtractiveSummary(longText.toString());
        Assertions.assertTrue(summary.endsWith("…"));
        Assertions.assertEquals(181, summary.length());
    }

    @Test
    void pageSummaryCacheShouldHitOnSecondRead() {
        DocumentService documentService = Mockito.mock(DocumentService.class);
        com.zhishiyun.kb.mapper.KbChunkMapper chunkMapper =
                Mockito.mock(com.zhishiyun.kb.mapper.KbChunkMapper.class);
        com.zhishiyun.kb.mapper.KbLibraryMapper libraryMapper =
                Mockito.mock(com.zhishiyun.kb.mapper.KbLibraryMapper.class);
        DocumentAiService service = new DocumentAiService(
                documentService, chunkMapper, libraryMapper,
                Mockito.mock(VectorSearchService.class),
                new com.fasterxml.jackson.databind.ObjectMapper());

        KbDocumentEntity doc = new KbDocumentEntity();
        doc.setId(99L);
        doc.setLibraryId(1L);
        doc.setLibraryCode("hr");
        doc.setTitle("cache-test");
        Mockito.when(documentService.getPermittedDocument(Mockito.eq(1L), Mockito.eq(99L))).thenReturn(doc);

        KbLibraryEntity lib = new KbLibraryEntity();
        lib.setName("人事制度库");
        Mockito.when(libraryMapper.selectById(1L)).thenReturn(lib);

        KbChunkEntity chunk = new KbChunkEntity();
        chunk.setContent("Annual Leave Policy employees may apply via HR portal.");
        chunk.setPageNo(1);
        chunk.setChunkIndex(0);
        Mockito.when(chunkMapper.selectList(Mockito.any()))
                .thenReturn(java.util.Collections.singletonList(chunk));

        PageSummaryResponse first = service.pageSummary(1L, 99L, 1);
        PageSummaryResponse second = service.pageSummary(1L, 99L, 1);
        Assertions.assertEquals(Boolean.FALSE, first.getCached());
        Assertions.assertEquals(Boolean.TRUE, second.getCached());
        Assertions.assertEquals(first.getSummary(), second.getSummary());
    }
}
