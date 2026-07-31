package com.zhishiyun.kb.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.entity.SysConfigEntity;
import com.zhishiyun.kb.mapper.SysConfigMapper;
import com.zhishiyun.kb.service.AuditService;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminModelService {

    public static final String CONFIG_KEY = "admin.model.config";

    private final SysConfigMapper sysConfigMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Value("${kb.llm.model:gpt-4o-mini}")
    private String llmModel;
    @Value("${kb.llm.endpoint:https://api.openai.com/v1}")
    private String llmEndpoint;
    @Value("${kb.llm.api-key:}")
    private String llmApiKey;
    @Value("${kb.embedding.model:text-embedding-3-small}")
    private String embeddingModel;
    @Value("${kb.embedding.endpoint:https://api.openai.com/v1}")
    private String embeddingEndpoint;
    @Value("${kb.embedding.api-key:}")
    private String embeddingApiKey;
    @Value("${kb.milvus.dimension:1536}")
    private int embeddingDimension;
    @Value("${kb.ocr.enabled:true}")
    private boolean ocrEnabled;
    @Value("${kb.ocr.base-url:}")
    private String ocrBaseUrl;
    @Value("${kb.vision.enabled:false}")
    private boolean visionEnabled;
    @Value("${kb.vision.model:gpt-4o}")
    private String visionModel;
    @Value("${kb.rag.context-n:6}")
    private int ragTopK;
    @Value("${kb.rag.score-threshold:0.15}")
    private double ragScoreThreshold;
    @Value("${kb.rate-limit.ask-per-minute:10}")
    private int askPerMinute;

    public Map<String, Object> getMaskedConfig() {
        Map<String, Object> cfg = loadMerged();
        maskKey(cfg, "llm");
        maskKey(cfg, "embedding");
        maskKey(cfg, "vision");
        return cfg;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> save(Long actorId, Map<String, Object> body) {
        Map<String, Object> current = loadMerged();
        if (body != null) {
            mergeSection(current, body, "llm");
            mergeSection(current, body, "embedding");
            mergeSection(current, body, "ocr");
            mergeSection(current, body, "vision");
            mergeSection(current, body, "rag");
        }
        persist(current);
        auditService.write(actorId, "MODEL_UPDATE", "system", "models", "更新模型配置");
        return getMaskedConfig();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> testConnection(String target) {
        Map<String, Object> cfg = loadMerged();
        String section = StringUtils.hasText(target) ? target : "llm";
        Map<String, Object> part = (Map<String, Object>) cfg.get(section);
        if (part == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未知目标: " + section);
        }
        String baseUrl = String.valueOf(part.get("baseUrl"));
        if (!StringUtils.hasText(baseUrl) || "null".equals(baseUrl)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "baseUrl 为空");
        }
        try {
            URL url = new URL(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            try {
                InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (in != null) {
                    in.close();
                }
            } catch (Exception ignored) {
            }
            Map<String, Object> res = new LinkedHashMap<String, Object>();
            res.put("ok", code > 0 && code < 500);
            res.put("httpStatus", code);
            res.put("message", code > 0 && code < 500 ? "连接可达" : "连接失败");
            return res;
        } catch (Exception e) {
            Map<String, Object> res = new LinkedHashMap<String, Object>();
            res.put("ok", false);
            res.put("message", "连接失败: " + e.getMessage());
            return res;
        }
    }

    /** 运行时读取（未掩码），供后续 LLM/Embedding 覆盖使用。 */
    public Map<String, Object> runtimeConfig() {
        return loadMerged();
    }

    @SuppressWarnings("unchecked")
    private void mergeSection(Map<String, Object> current, Map<String, Object> body, String key) {
        Object incoming = body.get(key);
        if (!(incoming instanceof Map)) {
            return;
        }
        Map<String, Object> src = (Map<String, Object>) incoming;
        Map<String, Object> dest = (Map<String, Object>) current.get(key);
        if (dest == null) {
            dest = new LinkedHashMap<String, Object>();
            current.put(key, dest);
        }
        for (Map.Entry<String, Object> e : src.entrySet()) {
            if ("apiKey".equals(e.getKey())) {
                String v = e.getValue() == null ? null : String.valueOf(e.getValue());
                if (!StringUtils.hasText(v) || v.contains("****")) {
                    continue;
                }
            }
            dest.put(e.getKey(), e.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private void maskKey(Map<String, Object> cfg, String section) {
        Object o = cfg.get(section);
        if (!(o instanceof Map)) {
            return;
        }
        Map<String, Object> m = (Map<String, Object>) o;
        Object key = m.get("apiKey");
        if (key != null && StringUtils.hasText(String.valueOf(key))) {
            String raw = String.valueOf(key);
            if (raw.length() <= 4) {
                m.put("apiKey", "sk-****");
            } else {
                m.put("apiKey", raw.substring(0, Math.min(3, raw.length())) + "****");
            }
            m.put("apiKeyConfigured", true);
        } else {
            m.put("apiKey", "");
            m.put("apiKeyConfigured", false);
        }
    }

    private Map<String, Object> loadMerged() {
        Map<String, Object> defaults = defaults();
        SysConfigEntity cfg = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigEntity>()
                .eq(SysConfigEntity::getConfigKey, CONFIG_KEY)
                .last("limit 1"));
        if (cfg == null || !StringUtils.hasText(cfg.getConfigValue())) {
            return defaults;
        }
        try {
            Map<String, Object> stored = objectMapper.readValue(cfg.getConfigValue(),
                    new TypeReference<Map<String, Object>>() {
                    });
            mergeSection(defaults, stored, "llm");
            mergeSection(defaults, stored, "embedding");
            mergeSection(defaults, stored, "ocr");
            mergeSection(defaults, stored, "vision");
            mergeSection(defaults, stored, "rag");
            return defaults;
        } catch (Exception e) {
            return defaults;
        }
    }

    private void persist(Map<String, Object> cfg) {
        try {
            String json = objectMapper.writeValueAsString(cfg);
            SysConfigEntity entity = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigEntity>()
                    .eq(SysConfigEntity::getConfigKey, CONFIG_KEY)
                    .last("limit 1"));
            if (entity == null) {
                entity = new SysConfigEntity();
                entity.setConfigKey(CONFIG_KEY);
                entity.setConfigValue(json);
                entity.setCreatedAt(LocalDateTime.now());
                entity.setUpdatedAt(LocalDateTime.now());
                sysConfigMapper.insert(entity);
            } else {
                entity.setConfigValue(json);
                entity.setUpdatedAt(LocalDateTime.now());
                sysConfigMapper.updateById(entity);
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "保存模型配置失败");
        }
    }

    private Map<String, Object> defaults() {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        Map<String, Object> llm = new LinkedHashMap<String, Object>();
        llm.put("provider", "openai-compatible");
        llm.put("baseUrl", llmEndpoint);
        llm.put("modelName", llmModel);
        llm.put("apiKey", llmApiKey);
        llm.put("temperature", 0.2);
        llm.put("maxTokens", 2048);
        llm.put("timeoutSec", 60);
        root.put("llm", llm);

        Map<String, Object> emb = new LinkedHashMap<String, Object>();
        emb.put("baseUrl", embeddingEndpoint);
        emb.put("modelName", embeddingModel);
        emb.put("apiKey", embeddingApiKey);
        emb.put("dimension", embeddingDimension);
        emb.put("shareWithLlm", false);
        root.put("embedding", emb);

        Map<String, Object> ocr = new LinkedHashMap<String, Object>();
        ocr.put("enabled", ocrEnabled);
        ocr.put("provider", "paddleocr");
        ocr.put("baseUrl", ocrBaseUrl);
        ocr.put("timeoutSec", 30);
        ocr.put("concurrency", 2);
        root.put("ocr", ocr);

        Map<String, Object> vision = new LinkedHashMap<String, Object>();
        vision.put("enabled", visionEnabled);
        vision.put("modelName", visionModel);
        vision.put("baseUrl", llmEndpoint);
        vision.put("apiKey", llmApiKey);
        root.put("vision", vision);

        Map<String, Object> rag = new LinkedHashMap<String, Object>();
        rag.put("topK", ragTopK);
        rag.put("scoreThreshold", ragScoreThreshold);
        rag.put("citeLimit", ragTopK);
        rag.put("askPerMinute", askPerMinute);
        root.put("rag", rag);
        return root;
    }
}
