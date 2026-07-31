package com.zhishiyun.kb.ingest;

import java.awt.image.BufferedImage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class JavaOcrService {

    @Value("${kb.ocr.enabled:true}")
    private boolean enabled;
    @Value("${kb.ocr.lang:chi_sim+eng}")
    private String lang;
    @Value("${kb.ocr.tessdata-path:}")
    private String tessDataPath;

    public OcrResult recognize(BufferedImage image) {
        if (!enabled) {
            return new OcrResult("", 0D);
        }
        try {
            ITesseract tesseract = new Tesseract();
            tesseract.setLanguage(lang);
            if (StringUtils.hasText(tessDataPath)) {
                tesseract.setDatapath(tessDataPath);
            }
            String text = tesseract.doOCR(image);
            return new OcrResult(text == null ? "" : text.trim(), estimateConfidence(text));
        } catch (TesseractException e) {
            log.warn("Java OCR failed: {}", e.getMessage());
            return new OcrResult("", 0D);
        }
    }

    private Double estimateConfidence(String text) {
        if (!StringUtils.hasText(text)) {
            return 0D;
        }
        int len = text.trim().length();
        if (len > 200) return 0.92D;
        if (len > 80) return 0.82D;
        if (len > 20) return 0.72D;
        return 0.55D;
    }

    @Data
    @AllArgsConstructor
    public static class OcrResult {
        private String text;
        private Double confidence;
    }
}
