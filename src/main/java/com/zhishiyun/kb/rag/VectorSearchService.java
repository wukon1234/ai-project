package com.zhishiyun.kb.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.infra.mysql.entity.KbChunkEntity;
import com.zhishiyun.kb.infra.mysql.mapper.KbChunkMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final KbChunkMapper kbChunkMapper;

    public List<SearchHit> search(String question, Set<String> libraryScopes, int topK) {
        List<KbChunkEntity> chunks = kbChunkMapper.selectList(new LambdaQueryWrapper<KbChunkEntity>()
                .in(KbChunkEntity::getLibraryCode, libraryScopes));
        return chunks.stream()
                .map(c -> new SearchHit(c, score(question, c.getContent())))
                .filter(h -> h.getScore() > 0)
                .sorted(Comparator.comparing(SearchHit::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private double score(String question, String content) {
        if (question == null || content == null) {
            return 0;
        }
        int hit = 0;
        String[] tokens = question.toLowerCase().split("\\s+");
        String lowerContent = content.toLowerCase();
        for (String token : tokens) {
            if (token.length() > 1 && lowerContent.contains(token)) {
                hit++;
            }
        }
        return ((double) hit) / Math.max(1, tokens.length);
    }

    @Data
    @AllArgsConstructor
    public static class SearchHit {
        private KbChunkEntity chunk;
        private double score;
    }
}
