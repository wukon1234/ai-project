package com.zhishiyun.kb.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhishiyun.kb.admin.service.AdminModelService;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * OpenAI 兼容 LLM 客户端：读取管理后台合并后的 llm 配置，调用 /chat/completions。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {

    private static final MediaType JSON = MediaType.parse("application/json");
    /** 对 429/503 等瞬时过载做有限重试。 */
    private static final int MAX_RETRY = 3;

    private final ObjectMapper objectMapper;
    private final AdminModelService adminModelService;

    /**
     * 基于 system/user 消息生成回答；失败抛业务异常供 SSE 转为 error 事件。
     */
    public String chat(String systemPrompt, String userPrompt) {
        Map<String, Object> llm = llmConfig();
        String baseUrl = str(llm.get("baseUrl"));
        String model = str(llm.get("modelName"));
        String apiKey = str(llm.get("apiKey"));
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "LLM 未配置 baseUrl/modelName");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "LLM apiKey 未配置");
        }
        int timeoutSec = intVal(llm.get("timeoutSec"), 60);
        double temperature = doubleVal(llm.get("temperature"), 0.2);
        int maxTokens = intVal(llm.get("maxTokens"), 2048);

        Exception last = null;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                return chatOnce(baseUrl, model, apiKey, timeoutSec, temperature, maxTokens,
                        systemPrompt, userPrompt, attempt);
            } catch (BizException e) {
                last = e;
                if (!isRetryable(e) || attempt >= MAX_RETRY) {
                    throw e;
                }
                sleepBackoff(attempt);
            } catch (Exception e) {
                last = e;
                if (attempt >= MAX_RETRY) {
                    break;
                }
                sleepBackoff(attempt);
            }
        }
        log.error("llm chat failed after retries", last);
        if (last instanceof BizException) {
            throw (BizException) last;
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR,
                last == null || last.getMessage() == null ? "LLM 调用异常" : last.getMessage());
    }

    private String chatOnce(
            String baseUrl,
            String model,
            String apiKey,
            int timeoutSec,
            double temperature,
            int maxTokens,
            String systemPrompt,
            String userPrompt,
            int attempt) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        ArrayNode messages = body.putArray("messages");
        if (StringUtils.hasText(systemPrompt)) {
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
        }
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userPrompt == null ? "" : userPrompt);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(Math.min(timeoutSec, 30)))
                .readTimeout(Duration.ofSeconds(timeoutSec))
                .callTimeout(Duration.ofSeconds(timeoutSec + 5))
                .build();
        Request request = new Request.Builder()
                .url(trimSlash(baseUrl) + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String respBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                log.warn("llm api failed attempt={} status={} body={}",
                        attempt, response.code(), abbreviate(respBody));
                String providerMsg = extractErrorMessage(respBody);
                String tip = response.code() == 503
                        ? "服务商繁忙，请稍后重试或在管理后台切换其它 LLM"
                        : ("LLM 调用失败 HTTP " + response.code());
                if (StringUtils.hasText(providerMsg)) {
                    tip = tip + "：" + providerMsg;
                }
                throw new BizException(ErrorCode.SYSTEM_ERROR, tip, response.code());
            }
            JsonNode root = objectMapper.readTree(respBody);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "LLM 返回空内容");
            }
            return content.trim();
        }
    }

    private static boolean isRetryable(BizException e) {
        Integer http = e.getHttpStatus();
        return http != null && (http == 429 || http == 502 || http == 503 || http == 504);
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(400L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String extractErrorMessage(String respBody) {
        if (!StringUtils.hasText(respBody)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(respBody);
            String msg = root.path("error").path("message").asText("");
            return StringUtils.hasText(msg) ? msg : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> llmConfig() {
        Object llm = adminModelService.runtimeConfig().get("llm");
        if (!(llm instanceof Map)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "LLM 配置缺失");
        }
        return (Map<String, Object>) llm;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static int intVal(Object o, int def) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o));
            } catch (Exception ignored) {
            }
        }
        return def;
    }

    private static double doubleVal(Object o, double def) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        if (o != null) {
            try {
                return Double.parseDouble(String.valueOf(o));
            } catch (Exception ignored) {
            }
        }
        return def;
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
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
