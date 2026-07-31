package com.zhishiyun.kb.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import lombok.Data;
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
 * GPT Vision 客户端：仅对 NEED_VISION 页调用，失败不影响已有 OCR 文本。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisionClient {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .callTimeout(java.time.Duration.ofSeconds(60))
            .build();

    @Value("${kb.vision.enabled:false}")
    private boolean enabled;
    @Value("${kb.vision.model:gpt-4o}")
    private String model;
    @Value("${kb.vision.endpoint:${kb.llm.endpoint:https://api.openai.com/v1}}")
    private String endpoint;
    @Value("${kb.vision.api-key:${kb.llm.api-key:}}")
    private String apiKey;
    @Value("${kb.vision.max-pages-per-doc:3}")
    private int maxPagesPerDoc;

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxPagesPerDoc() {
        return maxPagesPerDoc;
    }

    /**
     * 对页面图片做 Vision 转录；无密钥时返回基于 OCR 的 stub，便于本地验收。
     */
    public VisionResult describe(BufferedImage image, String ocrHint, int pageNo) {
        if (!enabled) {
            return null;
        }
        try {
            if (!StringUtils.hasText(apiKey)) {
                VisionResult stub = new VisionResult();
                stub.setText("[VISION-STUB] page=" + pageNo + " " + (ocrHint == null ? "" : ocrHint));
                stub.setSummary("stub vision summary for page " + pageNo);
                return stub;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bos);
            String b64 = Base64.getEncoder().encodeToString(bos.toByteArray());
            String prompt = "请转录页面文字并给出结构化摘要。OCR提示：" + (ocrHint == null ? "" : ocrHint);
            String json = "{"
                    + "\"model\":\"" + model + "\","
                    + "\"messages\":[{\"role\":\"user\",\"content\":["
                    + "{\"type\":\"text\",\"text\":" + objectMapper.writeValueAsString(prompt) + "},"
                    + "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64," + b64 + "\"}}"
                    + "]}],\"max_tokens\":800}";
            Request request = new Request.Builder()
                    .url(trimSlash(endpoint) + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("vision api failed status={}", response.code());
                    return null;
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                String content = root.path("choices").path(0).path("message").path("content").asText("");
                VisionResult result = new VisionResult();
                result.setText(content);
                result.setSummary(content.length() > 200 ? content.substring(0, 200) : content);
                return result;
            }
        } catch (Exception e) {
            log.warn("vision describe failed page={}", pageNo, e);
            return null;
        }
    }

    private String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Data
    public static class VisionResult {
        private String text;
        private String summary;
    }
}
