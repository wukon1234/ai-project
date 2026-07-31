package com.zhishiyun.kb.service;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DocumentAiServiceTest {

    @Test
    void shouldBuildShortExtractiveSummary() {
        DocumentAiService service = new DocumentAiService(
                Mockito.mock(com.zhishiyun.kb.service.DocumentService.class),
                Mockito.mock(com.zhishiyun.kb.mapper.KbChunkMapper.class),
                Mockito.mock(com.zhishiyun.kb.mapper.KbLibraryMapper.class),
                Mockito.mock(com.zhishiyun.kb.service.VectorSearchService.class),
                Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class),
                new com.fasterxml.jackson.databind.ObjectMapper());
        String summary = service.buildExtractiveSummary("员工入职满一年可享受年假。详情见手册第二十三页。");
        Assertions.assertTrue(summary.contains("年假"));
        Assertions.assertTrue(summary.length() <= 200);
    }

    @Test
    void shouldTruncateLongSummary() {
        DocumentAiService service = new DocumentAiService(
                Mockito.mock(com.zhishiyun.kb.service.DocumentService.class),
                Mockito.mock(com.zhishiyun.kb.mapper.KbChunkMapper.class),
                Mockito.mock(com.zhishiyun.kb.mapper.KbLibraryMapper.class),
                Mockito.mock(com.zhishiyun.kb.service.VectorSearchService.class),
                Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class),
                new com.fasterxml.jackson.databind.ObjectMapper());
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longText.append("字");
        }
        String summary = service.buildExtractiveSummary(longText.toString());
        Assertions.assertTrue(summary.endsWith("…"));
        Assertions.assertEquals(181, summary.length());
    }
}
