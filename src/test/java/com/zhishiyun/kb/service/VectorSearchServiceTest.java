package com.zhishiyun.kb.service;


import com.zhishiyun.kb.client.EmbeddingClient;
import com.zhishiyun.kb.entity.KbChunkEntity;
import com.zhishiyun.kb.mapper.KbChunkMapper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class VectorSearchServiceTest {

    @Test
    void searchInDocShouldOnlyReturnSameDocChunks() {
        KbChunkMapper mapper = Mockito.mock(KbChunkMapper.class);
        EmbeddingClient embeddingClient = Mockito.mock(EmbeddingClient.class);
        MilvusChunkService milvusChunkService = Mockito.mock(MilvusChunkService.class);
        Mockito.when(embeddingClient.embed(Mockito.anyList()))
                .thenThrow(new RuntimeException("unit-test-skip-milvus"));
        VectorSearchService service = new VectorSearchService(mapper, embeddingClient, milvusChunkService);

        KbChunkEntity c1 = new KbChunkEntity();
        c1.setId(1L);
        c1.setDocId(10L);
        c1.setPageNo(1);
        c1.setContent("年假规定入职满一年五天");

        KbChunkEntity c2 = new KbChunkEntity();
        c2.setId(2L);
        c2.setDocId(10L);
        c2.setPageNo(2);
        c2.setContent("报销流程需要提交发票");

        Mockito.when(mapper.selectList(Mockito.any())).thenReturn(Arrays.asList(c1, c2));

        List<VectorSearchService.SearchHit> hits = service.searchInDoc("年假规定", 10L, 5);
        Assertions.assertFalse(hits.isEmpty());
        Assertions.assertTrue(hits.stream().allMatch(h -> h.getChunk().getDocId().equals(10L)));
        Assertions.assertEquals(1L, hits.get(0).getChunk().getId());
    }

    @Test
    void relatedInDocShouldExcludeSeedPage() {
        KbChunkMapper mapper = Mockito.mock(KbChunkMapper.class);
        EmbeddingClient embeddingClient = Mockito.mock(EmbeddingClient.class);
        MilvusChunkService milvusChunkService = Mockito.mock(MilvusChunkService.class);
        Mockito.when(embeddingClient.embed(Mockito.anyList()))
                .thenThrow(new RuntimeException("unit-test-skip-milvus"));
        VectorSearchService service = new VectorSearchService(mapper, embeddingClient, milvusChunkService);

        KbChunkEntity p1 = new KbChunkEntity();
        p1.setId(1L);
        p1.setDocId(10L);
        p1.setPageNo(1);
        p1.setContent("年假规定入职满一年");

        KbChunkEntity p2 = new KbChunkEntity();
        p2.setId(2L);
        p2.setDocId(10L);
        p2.setPageNo(2);
        p2.setContent("年假申请在OA系统提交");

        Mockito.when(mapper.selectList(Mockito.any())).thenReturn(Arrays.asList(p1, p2));

        List<VectorSearchService.SearchHit> hits = service.relatedInDoc(10L, 1, "年假规定入职满一年", 5);
        Assertions.assertEquals(1, hits.size());
        Assertions.assertEquals(2, hits.get(0).getChunk().getPageNo().intValue());
    }
}
