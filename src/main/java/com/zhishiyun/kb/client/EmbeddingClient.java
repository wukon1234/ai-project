package com.zhishiyun.kb.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhishiyun.kb.admin.service.AdminModelService;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * OpenAI 兼容 Embedding 客户端：读取管理后台合并配置，调用 /embeddings。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingClient {

    private static final MediaType JSON = MediaType.parse("application/json");
    private static final int BATCH_SIZE = 16;
    private static final int MAX_RETRY = 3;

    private final ObjectMapper objectMapper;
    private final AdminModelService adminModelService;

    @Value("${kb.milvus.dimension:1024}")
    private int expectedDimension;

    /** 批量向量化；失败抛业务异常（入库任务应 FAILED）。 */
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<float[]>();
        }
        List<float[]> all = new ArrayList<float[]>(texts.size());
        for (int from = 0; from < texts.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, texts.size());
            all.addAll(embedBatch(texts.subList(from, to)));
        }
        return all;
    }

    public int expectedDimension() {
        Map<String, Object> emb = embConfig();
        Object dim = emb.get("dimension");
        if (dim instanceof Number) {
            return ((Number) dim).intValue();
        }
        return expectedDimension;
    }

    private List<float[]> embedBatch(List<String> batch) {
        Map<String, Object> emb = embConfig();
        String baseUrl = str(emb.get("baseUrl"));
        String model = str(emb.get("modelName"));
        String apiKey = str(emb.get("apiKey"));
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Embedding 未配置 baseUrl/modelName");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Embedding apiKey 未配置");
        }

        Exception last = null;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                return callOnce(baseUrl, model, apiKey, batch);
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                last = e;
                log.warn("embedding batch attempt {}/{} failed: {}", attempt, MAX_RETRY, e.getMessage());
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR,
                "Embedding 调用失败: " + (last == null ? "unknown" : last.getMessage()));
    }

    private List<float[]> callOnce(String baseUrl, String model, String apiKey, List<String> batch)
            throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode input = body.putArray("input");
        for (String t : batch) {
            input.add(t == null ? "" : t);
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(60))
                .callTimeout(Duration.ofSeconds(70))
                .build();
        Request request = new Request.Builder()
                .url(trimSlash(baseUrl) + "/embeddings")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String respBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                log.warn("embedding api failed status={} body={}", response.code(), abbreviate(respBody));
                throw new BizException(ErrorCode.SYSTEM_ERROR,
                        "Embedding 调用失败 HTTP " + response.code() + "：" + abbreviate(respBody));
            }
            JsonNode root = objectMapper.readTree(respBody);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() != batch.size()) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "Embedding 返回条数与输入不一致");
            }
            List<float[]> vectors = new ArrayList<float[]>(batch.size());
            // OpenAI 兼容：按 index 排序
            float[][] ordered = new float[batch.size()][];
            for (JsonNode item : data) {
                int idx = item.path("index").asInt(-1);
                JsonNode embNode = item.path("embedding");
                if (idx < 0 || idx >= batch.size() || !embNode.isArray()) {
                    throw new BizException(ErrorCode.SYSTEM_ERROR, "Embedding 返回格式无效");
                }
                float[] vec = new float[embNode.size()];
                for (int i = 0; i < embNode.size(); i++) {
                    vec[i] = (float) embNode.get(i).asDouble();
                }
                int expect = expectedDimension();
                if (vec.length != expect) {
                    throw new BizException(ErrorCode.SYSTEM_ERROR,
                            "Embedding 维度不匹配: actual=" + vec.length + ", expect=" + expect);
                }
                ordered[idx] = vec;
            }
            for (int i = 0; i < ordered.length; i++) {
                if (ordered[i] == null) {
                    throw new BizException(ErrorCode.SYSTEM_ERROR, "Embedding 缺少 index=" + i);
                }
                vectors.add(ordered[i]);
            }
            return vectors;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> embConfig() {
        Object emb = adminModelService.runtimeConfig().get("embedding");
        if (!(emb instanceof Map)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Embedding 配置缺失");
        }
        return (Map<String, Object>) emb;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 240 ? s.substring(0, 240) : s;
    }
}
