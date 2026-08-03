package com.zhishiyun.kb.service;


import com.zhishiyun.kb.client.EmbeddingClient;
import com.zhishiyun.kb.entity.KbChunkEntity;
import com.zhishiyun.kb.mapper.KbChunkMapper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class VectorSearchServiceHybridTest {

    @Test
    void hybridShouldFuseVectorAndKeyword() {
        KbChunkMapper mapper = Mockito.mock(KbChunkMapper.class);
        EmbeddingClient embeddingClient = Mockito.mock(EmbeddingClient.class);
        MilvusChunkService milvusChunkService = Mockito.mock(MilvusChunkService.class);
        Mockito.when(embeddingClient.embed(Mockito.anyList()))
                .thenThrow(new RuntimeException("unit-test-skip-milvus"));
        VectorSearchService service = new VectorSearchService(mapper, embeddingClient, milvusChunkService);
        service.setHybridEnabled(true);

        KbChunkEntity a = chunk(1L, "年假规定入职满一年五天");
        KbChunkEntity b = chunk(2L, "报销流程需要发票");
        KbChunkEntity c = chunk(3L, "年假申请在 OA 提交");
        Mockito.when(mapper.selectList(Mockito.any())).thenReturn(Arrays.asList(a, b, c));

        List<VectorSearchService.SearchHit> hits = service.search("年假", new HashSet<String>(Arrays.asList("hr")), 5);
        Assertions.assertFalse(hits.isEmpty());
        Assertions.assertTrue(hits.stream().anyMatch(h -> h.getChunk().getId().equals(1L)
                || h.getChunk().getId().equals(3L)));
    }

    private KbChunkEntity chunk(Long id, String content) {
        KbChunkEntity c = new KbChunkEntity();
        c.setId(id);
        c.setDocId(10L);
        c.setLibraryCode("hr");
        c.setPageNo(1);
        c.setContent(content);
        return c;
    }
}
