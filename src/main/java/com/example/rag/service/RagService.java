package com.example.rag.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagService {

    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel chatModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public RagService(EmbeddingModel embeddingModel,
                      ChatLanguageModel chatModel,
                      EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
        this.embeddingStore = embeddingStore;
    }

    public void addDocuments(List<String> documents, String sourceName) {
        for (int i = 0; i < documents.size(); i++) {
            String doc = documents.get(i);
            Metadata metadata = new Metadata();
            metadata.put("source", sourceName);
            metadata.put("chunk_index", String.valueOf(i));
            metadata.put("timestamp", String.valueOf(System.currentTimeMillis()));

            TextSegment segment = TextSegment.from(doc, metadata);
            Response<Embedding> embeddingResponse = embeddingModel.embed(doc);
            Embedding embedding = embeddingResponse.content();
            embeddingStore.add(doc, embedding);
        }
        log.info("Added {} segments from {}", documents.size(), sourceName);
    }

    public Map<String, Object> query(String question) {
        Map<String, Object> result = new HashMap<>();

        try {
            Response<Embedding> embeddingResponse = embeddingModel.embed(question);
            Embedding queryEmbedding = embeddingResponse.content();

            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(5)
                    .minScore(0.6)
                    .build();

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

            List<String> contextStrings = matches.stream()
                    .map(m -> m.embedded().text())
                    .collect(Collectors.toList());

            List<ChatMessage> messages = new ArrayList<>();

            String systemPrompt = "你是一个企业知识库助手。请严格根据提供的参考资料回答问题。如果参考资料中没有相关信息，请回复\"知识库中未找到相关信息\"。回答时请引用来源。";
            messages.add(SystemMessage.from(systemPrompt));

            String contextStr = contextStrings.isEmpty() ? "无相关上下文" :
                    String.join("\n\n---\n\n", contextStrings);
            String userPrompt = String.format("问题：%s\n\n参考资料：\n%s", question, contextStr);
            messages.add(UserMessage.from(userPrompt));

            Response<AiMessage> response = chatModel.generate(messages);
            String answer = response.content().text();

            List<Map<String, Object>> sources = matches.stream()
                    .map(m -> {
                        Map<String, Object> source = new HashMap<>();
                        TextSegment segment = m.embedded();
                        String text = segment.text();
                        source.put("text", text.substring(0, Math.min(200, text.length())));
                        source.put("metadata", segment.metadata().toMap());
                        source.put("score", m.score());
                        return source;
                    })
                    .collect(Collectors.toList());

            result.put("answer", answer);
            result.put("sources", sources);
            result.put("success", true);

        } catch (Exception e) {
            log.error("Query failed", e);
            result.put("answer", "查询失败: " + e.getMessage());
            result.put("sources", Collections.emptyList());
            result.put("success", false);
        }

        return result;
    }

    public List<Map<String, Object>> searchOnly(String question, int maxResults) {
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            Response<Embedding> embeddingResponse = embeddingModel.embed(question);
            Embedding queryEmbedding = embeddingResponse.content();

            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(0.5)
                    .build();

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

            for (EmbeddingMatch<TextSegment> match : matches) {
                Map<String, Object> item = new HashMap<>();
                item.put("text", match.embedded().text());
                item.put("metadata", match.embedded().metadata().toMap());
                item.put("score", match.score());
                results.add(item);
            }

        } catch (Exception e) {
            log.error("Search failed", e);
        }

        return results;
    }
}
