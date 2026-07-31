package com.zhishiyun.kb.client;


import com.zhishiyun.kb.service.JavaOcrService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * PaddleOCR HTTP 客户端：超时 60s，失败重试；未配置 base-url 时回落本地 Tesseract。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrClient {

    private final ObjectMapper objectMapper;
    private final JavaOcrService javaOcrService;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build();

    @Value("${kb.ocr.enabled:true}")
    private boolean enabled;
    @Value("${kb.ocr.base-url:${kb.ocr.endpoint:}}")
    private String baseUrl;
    @Value("${kb.ocr.retry:3}")
    private int retry;
    @Value("${kb.ocr.page-fail-strategy:fail}")
    private String pageFailStrategy;

    public boolean isRemoteEnabled() {
        return enabled && StringUtils.hasText(baseUrl);
    }

    public boolean skipOnPageFail() {
        return "skip".equalsIgnoreCase(pageFailStrategy);
    }

    /** 对页面图像做 OCR。 */
    public OcrResult recognizeImage(BufferedImage image) throws IOException {
        if (!enabled) {
            return new OcrResult("", 0D, java.util.Collections.<Object>emptyList());
        }
        if (!isRemoteEnabled()) {
            JavaOcrService.OcrResult local = javaOcrService.recognize(image);
            return new OcrResult(local.getText(), local.getConfidence(), java.util.Collections.<Object>emptyList());
        }
        byte[] png = toPng(image);
        IOException last = null;
        int attempts = Math.max(1, retry);
        for (int i = 1; i <= attempts; i++) {
            try {
                return postMultipart("/ocr/image", png, "page.png");
            } catch (IOException ex) {
                last = ex;
                log.warn("ocr image attempt {}/{} failed: {}", i, attempts, ex.getMessage());
            }
        }
        throw new IOException("OCR 服务不可用: " + (last == null ? "unknown" : last.getMessage()), last);
    }

    private OcrResult postMultipart(String path, byte[] bytes, String filename) throws IOException {
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                        "file",
                        filename,
                        RequestBody.create(bytes, MediaType.parse("image/png")))
                .build();
        Request request = new Request.Builder()
                .url(trimSlash(baseUrl) + path)
                .post(body)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("OCR HTTP " + response.code());
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            String text = root.path("text").asText("");
            double confidence = root.path("confidence").asDouble(0D);
            return new OcrResult(text, confidence, java.util.Collections.<Object>emptyList());
        }
    }

    private byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bos);
        return bos.toByteArray();
    }

    private String trimSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    @Data
    @AllArgsConstructor
    public static class OcrResult {
        private String text;
        private Double confidence;
        private java.util.List<Object> boxes;
    }
}
