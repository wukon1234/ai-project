package com.zhishiyun.kb.stats;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.infra.mysql.entity.ChatMessageEntity;
import com.zhishiyun.kb.infra.mysql.entity.FavAnswerEntity;
import com.zhishiyun.kb.infra.mysql.entity.FavDocumentEntity;
import com.zhishiyun.kb.infra.mysql.entity.FeedbackRecordEntity;
import com.zhishiyun.kb.infra.mysql.entity.UsageEventEntity;
import com.zhishiyun.kb.infra.mysql.mapper.ChatMessageMapper;
import com.zhishiyun.kb.infra.mysql.mapper.FavAnswerMapper;
import com.zhishiyun.kb.infra.mysql.mapper.FavDocumentMapper;
import com.zhishiyun.kb.infra.mysql.mapper.FeedbackRecordMapper;
import com.zhishiyun.kb.infra.mysql.mapper.UsageEventMapper;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 使用统计聚合：基于 usage_event / 反馈 / 收藏表计算 KPI、趋势、分布等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter UPDATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UsageEventMapper usageEventMapper;
    private final FeedbackRecordMapper feedbackRecordMapper;
    private final FavDocumentMapper favDocumentMapper;
    private final FavAnswerMapper favAnswerMapper;
    private final ChatMessageMapper chatMessageMapper;

    /** 单次提问预估节省分钟数，可配置 */
    @Value("${kb.stats.minutes-saved-per-ask:4}")
    private int minutesSavedPerAsk;

    /** 聚合指定时间窗内的 KPI、趋势、分布与成就等。 */
    public Map<String, Object> overview(Long userId, String range, String from, String to) {
        RangeWindow window = resolveRange(range, from, to);
        List<UsageEventEntity> events = loadEvents(userId, window.from, window.to);
        List<UsageEventEntity> prevEvents = loadEvents(userId, window.prevFrom, window.prevTo);

        int askCount = countType(events, "ASK");
        int prevAsk = countType(prevEvents, "ASK");
        double askMom = prevAsk == 0 ? (askCount > 0 ? 100D : 0D)
                : ((askCount - prevAsk) * 100D / prevAsk);

        long favDoc = nullToZero(favDocumentMapper.selectCount(new LambdaQueryWrapper<FavDocumentEntity>()
                .eq(FavDocumentEntity::getUserId, userId)));
        long favAns = nullToZero(favAnswerMapper.selectCount(new LambdaQueryWrapper<FavAnswerEntity>()
                .eq(FavAnswerEntity::getUserId, userId)));
        long favTotal = favDoc + favAns;
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        long favMonthNew = countType(loadEvents(userId, monthStart, LocalDateTime.now()), "FAVORITE");

        RatingAgg rating = loadRating(userId, window.from, window.to);
        FeedbackAgg feedback = loadFeedback(userId, window.from, window.to);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("range", window.rangeLabel);
        data.put("from", window.from.format(DAY_FMT));
        data.put("to", window.to.toLocalDate().format(DAY_FMT));
        data.put("updatedAt", LocalDateTime.now().format(UPDATE_FMT));

        Map<String, Object> kpi = new LinkedHashMap<String, Object>();
        kpi.put("askCount", askCount);
        kpi.put("askMomPercent", round1(askMom));
        kpi.put("savedHours", round1(askCount * minutesSavedPerAsk / 60D));
        kpi.put("avgRating", rating.avg);
        kpi.put("ratingCount", rating.count);
        kpi.put("favoriteCount", favTotal);
        kpi.put("favoriteMonthNew", favMonthNew);
        data.put("kpi", kpi);

        data.put("askTrend", buildAskTrend(events, window.from.toLocalDate(), window.to.toLocalDate()));
        data.put("libraryDistribution", buildLibraryDist(events));
        data.put("topQuestions", buildTopQuestions(userId, events));
        data.put("feedbackOverview", feedback.toMap());
        data.put("achievements", buildAchievements(userId, askCount, rating, feedback,
                countType(events, "READ_COMPLETE")));
        data.put("activeHeatmap", buildHeatmap(events));
        Map<String, Object> sourceHabit = new LinkedHashMap<String, Object>();
        sourceHabit.put("openSourceCount", countType(events, "OPEN_SOURCE"));
        sourceHabit.put("readCompleteCount", countType(events, "READ_COMPLETE"));
        sourceHabit.put("avgReadMinutes", avgReadMinutes(events));
        data.put("sourceHabit", sourceHabit);
        log.info("stats overview, userId={}, range={}, askCount={}, events={}",
                userId, window.rangeLabel, askCount, events.size());
        return data;
    }

    /** 导出概览 KPI 为 CSV（含 UTF-8 BOM）。 */
    public byte[] exportCsv(Long userId, String range, String from, String to) {
        Map<String, Object> overview = overview(userId, range, from, to);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            // UTF-8 BOM，方便 Excel 打开中文
            bos.write(0xEF);
            bos.write(0xBB);
            bos.write(0xBF);
            OutputStreamWriter writer = new OutputStreamWriter(bos, StandardCharsets.UTF_8);
            writer.write("指标,值\n");
            @SuppressWarnings("unchecked")
            Map<String, Object> kpi = (Map<String, Object>) overview.get("kpi");
            writer.write("提问次数," + kpi.get("askCount") + "\n");
            writer.write("环比%," + kpi.get("askMomPercent") + "\n");
            writer.write("预估节省小时," + kpi.get("savedHours") + "\n");
            writer.write("平均评分," + kpi.get("avgRating") + "\n");
            writer.write("收藏数," + kpi.get("favoriteCount") + "\n");
            writer.write("范围," + overview.get("from") + "~" + overview.get("to") + "\n");
            writer.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            log.error("stats csv export failed, userId={}, range={}", userId, range, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "导出失败");
        }
    }

    private List<UsageEventEntity> loadEvents(Long userId, LocalDateTime from, LocalDateTime to) {
        return usageEventMapper.selectList(new LambdaQueryWrapper<UsageEventEntity>()
                .eq(UsageEventEntity::getUserId, userId)
                .ge(UsageEventEntity::getEventTime, from)
                .le(UsageEventEntity::getEventTime, to));
    }

    private int countType(List<UsageEventEntity> events, String type) {
        int n = 0;
        for (UsageEventEntity e : events) {
            if (type.equals(e.getEventType())) {
                n++;
            }
        }
        return n;
    }

    private List<Map<String, Object>> buildAskTrend(List<UsageEventEntity> events, LocalDate from, LocalDate to) {
        Map<String, Integer> dayCount = new LinkedHashMap<String, Integer>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            dayCount.put(d.format(DAY_FMT), 0);
        }
        for (UsageEventEntity e : events) {
            if (!"ASK".equals(e.getEventType()) || e.getEventTime() == null) {
                continue;
            }
            String key = e.getEventTime().toLocalDate().format(DAY_FMT);
            if (dayCount.containsKey(key)) {
                dayCount.put(key, dayCount.get(key) + 1);
            }
        }
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, Integer> entry : dayCount.entrySet()) {
            Map<String, Object> row = new HashMap<String, Object>();
            row.put("date", entry.getKey());
            row.put("count", entry.getValue());
            list.add(row);
        }
        return list;
    }

    private List<Map<String, Object>> buildLibraryDist(List<UsageEventEntity> events) {
        Map<String, Integer> dist = new HashMap<String, Integer>();
        for (UsageEventEntity e : events) {
            if (!"ASK".equals(e.getEventType())) {
                continue;
            }
            String code = StringUtils.hasText(e.getLibraryCode()) ? e.getLibraryCode() : "unknown";
            dist.put(code, dist.containsKey(code) ? dist.get(code) + 1 : 1);
        }
        int total = 0;
        for (Integer v : dist.values()) {
            total += v;
        }
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, Integer> entry : dist.entrySet()) {
            Map<String, Object> row = new HashMap<String, Object>();
            row.put("libraryCode", entry.getKey());
            row.put("count", entry.getValue());
            row.put("percent", total == 0 ? 0 : round1(entry.getValue() * 100D / total));
            list.add(row);
        }
        Collections.sort(list, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                return Integer.compare((Integer) b.get("count"), (Integer) a.get("count"));
            }
        });
        return list;
    }

    private List<Map<String, Object>> buildTopQuestions(Long userId, List<UsageEventEntity> events) {
        Map<String, Integer> qCount = new HashMap<String, Integer>();
        for (UsageEventEntity e : events) {
            if (!"ASK".equals(e.getEventType()) || !StringUtils.hasText(e.getRefId())) {
                continue;
            }
            // refId 可能是 messageId；优先用 extra_json 中的 question，否则回查消息
            String q = extractQuestion(e);
            if (!StringUtils.hasText(q)) {
                continue;
            }
            String key = q.trim();
            qCount.put(key, qCount.containsKey(key) ? qCount.get(key) + 1 : 1);
        }
        return qCount.entrySet().stream()
                .sorted(new Comparator<Map.Entry<String, Integer>>() {
                    @Override
                    public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                        return Integer.compare(b.getValue(), a.getValue());
                    }
                })
                .limit(5)
                .map(entry -> {
                    Map<String, Object> row = new HashMap<String, Object>();
                    row.put("question", entry.getKey());
                    row.put("count", entry.getValue());
                    return row;
                })
                .collect(Collectors.toList());
    }

    private String extractQuestion(UsageEventEntity e) {
        if (StringUtils.hasText(e.getExtraJson()) && e.getExtraJson().contains("\"question\"")) {
            int i = e.getExtraJson().indexOf("\"question\"");
            int colon = e.getExtraJson().indexOf(':', i);
            int start = e.getExtraJson().indexOf('"', colon + 1);
            int end = e.getExtraJson().indexOf('"', start + 1);
            if (start >= 0 && end > start) {
                return e.getExtraJson().substring(start + 1, end);
            }
        }
        if (StringUtils.hasText(e.getRefId()) && e.getRefId().matches("\\d+")) {
            ChatMessageEntity msg = chatMessageMapper.selectById(Long.valueOf(e.getRefId()));
            if (msg != null && "user".equals(msg.getRole())) {
                return msg.getContent();
            }
        }
        return e.getRefId();
    }

    private List<Map<String, Object>> buildAchievements(Long userId, int askCount, RatingAgg rating,
                                                         FeedbackAgg feedback, int readComplete) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        list.add(achievement("知识探索者", "累计提问达 10 次", askCount, 10));
        list.add(achievement("精准提问", "给出评分达 5 次", rating.count, 5));
        list.add(achievement("深度阅读", "完整阅读达 5 次", readComplete, 5));
        list.add(achievement("多轮达人", "有帮助反馈达 3 次", feedback.helpful, 3));
        return list;
    }

    private Map<String, Object> achievement(String name, String desc, int current, int target) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", name);
        m.put("description", desc);
        m.put("current", current);
        m.put("target", target);
        m.put("completed", current >= target);
        m.put("progress", Math.min(100, (int) Math.round(current * 100D / target)));
        return m;
    }

    private List<Map<String, Object>> buildHeatmap(List<UsageEventEntity> events) {
        // 7(日)~1(一) × 0~23 小时格子
        int[][] grid = new int[7][24];
        for (UsageEventEntity e : events) {
            if (e.getEventTime() == null) {
                continue;
            }
            int dow = e.getEventTime().getDayOfWeek().getValue() % 7; // 周日=0
            int hour = e.getEventTime().getHour();
            grid[dow][hour]++;
        }
        List<Map<String, Object>> cells = new ArrayList<Map<String, Object>>();
        for (int d = 0; d < 7; d++) {
            for (int h = 0; h < 24; h++) {
                if (grid[d][h] == 0) {
                    continue;
                }
                Map<String, Object> cell = new HashMap<String, Object>();
                cell.put("weekday", d);
                cell.put("hour", h);
                cell.put("count", grid[d][h]);
                cells.add(cell);
            }
        }
        return cells;
    }

    private Double avgReadMinutes(List<UsageEventEntity> events) {
        double sum = 0;
        int n = 0;
        for (UsageEventEntity e : events) {
            if (!"READ_COMPLETE".equals(e.getEventType()) || !StringUtils.hasText(e.getExtraJson())) {
                continue;
            }
            String json = e.getExtraJson();
            int idx = json.indexOf("readMinutes");
            if (idx < 0) {
                continue;
            }
            String num = json.replaceAll(".*?readMinutes\"?\\s*[:=]\\s*([0-9.]+).*", "$1");
            try {
                sum += Double.parseDouble(num);
                n++;
            } catch (Exception ignored) {
            }
        }
        return n == 0 ? null : round1(sum / n);
    }

    private RatingAgg loadRating(Long userId, LocalDateTime from, LocalDateTime to) {
        List<FeedbackRecordEntity> rows = feedbackRecordMapper.selectList(new LambdaQueryWrapper<FeedbackRecordEntity>()
                .eq(FeedbackRecordEntity::getUserId, userId)
                .eq(FeedbackRecordEntity::getType, "RATING")
                .ge(FeedbackRecordEntity::getCreatedAt, from)
                .le(FeedbackRecordEntity::getCreatedAt, to));
        int count = 0;
        double sum = 0;
        for (FeedbackRecordEntity r : rows) {
            if (r.getRatingScore() == null) {
                continue;
            }
            sum += r.getRatingScore();
            count++;
        }
        RatingAgg agg = new RatingAgg();
        agg.count = count;
        agg.avg = count == 0 ? null : round1(sum / count);
        return agg;
    }

    private FeedbackAgg loadFeedback(Long userId, LocalDateTime from, LocalDateTime to) {
        List<FeedbackRecordEntity> rows = feedbackRecordMapper.selectList(new LambdaQueryWrapper<FeedbackRecordEntity>()
                .eq(FeedbackRecordEntity::getUserId, userId)
                .ge(FeedbackRecordEntity::getCreatedAt, from)
                .le(FeedbackRecordEntity::getCreatedAt, to));
        FeedbackAgg agg = new FeedbackAgg();
        for (FeedbackRecordEntity r : rows) {
            if ("HELPFUL".equals(r.getType())) {
                agg.helpful++;
            } else if ("UNHELPFUL".equals(r.getType())) {
                agg.unhelpful++;
            }
        }
        return agg;
    }

    private RangeWindow resolveRange(String range, String from, String to) {
        String r = StringUtils.hasText(range) ? range : "30d";
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start;
        if ("7d".equalsIgnoreCase(r)) {
            start = end.minusDays(6).with(LocalTime.MIN);
        } else if ("30d".equalsIgnoreCase(r)) {
            start = end.minusDays(29).with(LocalTime.MIN);
        } else if ("quarter".equalsIgnoreCase(r)) {
            // Java 8：按自然季度起点（1/4/7/10 月）
            LocalDate now = LocalDate.now();
            int qStartMonth = ((now.getMonthValue() - 1) / 3) * 3 + 1;
            start = LocalDate.of(now.getYear(), qStartMonth, 1).atStartOfDay();
        } else if ("custom".equalsIgnoreCase(r)) {
            if (!StringUtils.hasText(from) || !StringUtils.hasText(to)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "custom 范围需要 from/to");
            }
            start = LocalDate.parse(from).atStartOfDay();
            end = LocalDate.parse(to).atTime(LocalTime.MAX);
        } else {
            throw new BizException(ErrorCode.PARAM_INVALID, "range 仅支持 7d|30d|quarter|custom");
        }
        long days = Math.max(1, java.time.Duration.between(start, end).toDays() + 1);
        RangeWindow w = new RangeWindow();
        w.rangeLabel = r;
        w.from = start;
        w.to = end;
        w.prevTo = start.minusSeconds(1);
        w.prevFrom = w.prevTo.minusDays(days - 1).with(LocalTime.MIN);
        return w;
    }

    private double round1(double v) {
        return Math.round(v * 10D) / 10D;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private static class RangeWindow {
        String rangeLabel;
        LocalDateTime from;
        LocalDateTime to;
        LocalDateTime prevFrom;
        LocalDateTime prevTo;
    }

    private static class RatingAgg {
        int count;
        Double avg;
    }

    private static class FeedbackAgg {
        int helpful;
        int unhelpful;

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            int total = helpful + unhelpful;
            m.put("helpful", helpful);
            m.put("unhelpful", unhelpful);
            m.put("helpfulPercent", total == 0 ? 0 : Math.round(helpful * 100D / total));
            m.put("optimizedHint", "您的反馈帮助优化了 " + helpful + " 条知识");
            return m;
        }
    }
}
