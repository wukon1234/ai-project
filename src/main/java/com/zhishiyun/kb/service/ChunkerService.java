package com.zhishiyun.kb.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** 文本分块：固定窗口 + overlap，供入库与检索使用。 */
@Service
public class ChunkerService {

    /** 按字符窗口切分，步进 = chunkSize - overlap。 */
    public List<String> split(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<String>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }
        String normalized = text.replace("\r", "").trim();
        int step = Math.max(1, chunkSize - overlap);
        int idx = 0;
        while (idx < normalized.length()) {
            int end = Math.min(normalized.length(), idx + chunkSize);
            chunks.add(normalized.substring(idx, end).trim());
            if (end >= normalized.length()) {
                break;
            }
            idx += step;
        }
        return chunks;
    }
}
