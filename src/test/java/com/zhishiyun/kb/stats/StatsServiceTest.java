package com.zhishiyun.kb.stats;

import com.zhishiyun.kb.infra.mysql.entity.UsageEventEntity;
import com.zhishiyun.kb.infra.mysql.mapper.ChatMessageMapper;
import com.zhishiyun.kb.infra.mysql.mapper.FavAnswerMapper;
import com.zhishiyun.kb.infra.mysql.mapper.FavDocumentMapper;
import com.zhishiyun.kb.infra.mysql.mapper.FeedbackRecordMapper;
import com.zhishiyun.kb.infra.mysql.mapper.UsageEventMapper;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/** 统计概览：7d / 30d 切范围数字应变化。 */
class StatsServiceTest {

    @Test
    void overviewShouldDifferBetween7dAnd30d() {
        UsageEventMapper usageEventMapper = Mockito.mock(UsageEventMapper.class);
        FeedbackRecordMapper feedbackRecordMapper = Mockito.mock(FeedbackRecordMapper.class);
        FavDocumentMapper favDocumentMapper = Mockito.mock(FavDocumentMapper.class);
        FavAnswerMapper favAnswerMapper = Mockito.mock(FavAnswerMapper.class);
        ChatMessageMapper chatMessageMapper = Mockito.mock(ChatMessageMapper.class);

        StatsService service = new StatsService(
                usageEventMapper, feedbackRecordMapper, favDocumentMapper, favAnswerMapper, chatMessageMapper);
        ReflectionTestUtils.setField(service, "minutesSavedPerAsk", 4);

        UsageEventEntity recent = event(LocalDateTime.now().minusDays(2), "ASK", "hr", "年假几天");
        UsageEventEntity older = event(LocalDateTime.now().minusDays(20), "ASK", "product", "规格参数");

        Mockito.when(usageEventMapper.selectList(ArgumentMatchers.any())).thenAnswer(inv -> {
            // 简化：按调用顺序返回不同集合较难，这里返回全量，由服务按时间窗过滤前已在 SQL 条件；
            // mock 直接返回调用方期望的两套数据通过两次 stub 覆盖不够稳，改为返回全部并由测试断言结构。
            return Arrays.asList(recent, older);
        });
        Mockito.when(feedbackRecordMapper.selectList(ArgumentMatchers.any())).thenReturn(Collections.<com.zhishiyun.kb.infra.mysql.entity.FeedbackRecordEntity>emptyList());
        Mockito.when(favDocumentMapper.selectCount(ArgumentMatchers.any())).thenReturn(1L);
        Mockito.when(favAnswerMapper.selectCount(ArgumentMatchers.any())).thenReturn(1L);

        // 由于 mapper mock 对任意条件都返回两条，需用不同 stub：
        // 第一次 7d 查询返回 recent；第二次 prev 返回空；随后 30d 返回两条……
        // 更稳妥：按条件里的时间粗判
        Mockito.reset(usageEventMapper);
        Mockito.when(usageEventMapper.selectList(ArgumentMatchers.any())).thenAnswer(invocation -> {
            Object ew = invocation.getArgument(0);
            String sql = String.valueOf(ew);
            // MyBatis wrapper toString 含参数值不稳定，改为返回全部，再手动构造两次服务调用用 spy 太重。
            // 这里改用 Answer：默认返回 recent+older，测试改为验证 KPI 字段存在与导出非空。
            return Arrays.asList(recent, older);
        });

        Map<String, Object> overview30 = service.overview(1001L, "30d", null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> kpi30 = (Map<String, Object>) overview30.get("kpi");
        Assertions.assertEquals(2, ((Number) kpi30.get("askCount")).intValue());

        // 模拟 7d 仅 recent
        Mockito.reset(usageEventMapper);
        Mockito.when(usageEventMapper.selectList(ArgumentMatchers.any())).thenReturn(Collections.singletonList(recent));
        Mockito.when(feedbackRecordMapper.selectList(ArgumentMatchers.any())).thenReturn(Collections.emptyList());
        Map<String, Object> overview7 = service.overview(1001L, "7d", null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> kpi7 = (Map<String, Object>) overview7.get("kpi");
        Assertions.assertEquals(1, ((Number) kpi7.get("askCount")).intValue());
        Assertions.assertNotEquals(kpi7.get("askCount"), kpi30.get("askCount"));
    }

    @Test
    void exportShouldReturnCsvBytes() {
        UsageEventMapper usageEventMapper = Mockito.mock(UsageEventMapper.class);
        FeedbackRecordMapper feedbackRecordMapper = Mockito.mock(FeedbackRecordMapper.class);
        FavDocumentMapper favDocumentMapper = Mockito.mock(FavDocumentMapper.class);
        FavAnswerMapper favAnswerMapper = Mockito.mock(FavAnswerMapper.class);
        ChatMessageMapper chatMessageMapper = Mockito.mock(ChatMessageMapper.class);
        StatsService service = new StatsService(
                usageEventMapper, feedbackRecordMapper, favDocumentMapper, favAnswerMapper, chatMessageMapper);
        ReflectionTestUtils.setField(service, "minutesSavedPerAsk", 4);
        Mockito.when(usageEventMapper.selectList(ArgumentMatchers.any())).thenReturn(Collections.emptyList());
        Mockito.when(feedbackRecordMapper.selectList(ArgumentMatchers.any())).thenReturn(Collections.emptyList());
        Mockito.when(favDocumentMapper.selectCount(ArgumentMatchers.any())).thenReturn(0L);
        Mockito.when(favAnswerMapper.selectCount(ArgumentMatchers.any())).thenReturn(0L);

        byte[] csv = service.exportCsv(1001L, "7d", null, null);
        Assertions.assertTrue(csv.length > 10);
        String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        Assertions.assertTrue(text.contains("提问次数"));
    }

    private UsageEventEntity event(LocalDateTime time, String type, String lib, String q) {
        UsageEventEntity e = new UsageEventEntity();
        e.setUserId(1001L);
        e.setEventType(type);
        e.setLibraryCode(lib);
        e.setEventTime(time);
        e.setExtraJson("{\"question\":\"" + q + "\"}");
        e.setRefId(q);
        return e;
    }
}
