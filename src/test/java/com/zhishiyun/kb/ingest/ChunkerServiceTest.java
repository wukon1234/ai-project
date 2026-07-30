package com.zhishiyun.kb.ingest;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ChunkerServiceTest {

    private final ChunkerService chunkerService = new ChunkerService();

    @Test
    void shouldSplitWithOverlap() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 1200; i++) {
            builder.append("字");
        }
        List<String> chunks = chunkerService.split(builder.toString(), 500, 100);
        Assertions.assertTrue(chunks.size() >= 3);
        Assertions.assertEquals(500, chunks.get(0).length());
        Assertions.assertTrue(chunks.get(1).length() > 0);
        Assertions.assertEquals(chunks.get(0).substring(400, 500), chunks.get(1).substring(0, 100));
    }
}
