package com.zhishiyun.kb.history;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.infra.mysql.entity.ChatSessionEntity;
import com.zhishiyun.kb.infra.mysql.mapper.ChatSessionMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class HistoryService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ChatSessionMapper chatSessionMapper;

    public List<Map<String, Object>> history(Long userId, String keyword) {
        List<ChatSessionEntity> sessions = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getUserId, userId)
                .eq(ChatSessionEntity::getDeleted, 0)
                .orderByDesc(ChatSessionEntity::getUpdatedAt));
        if (StringUtils.hasText(keyword)) {
            sessions = sessions.stream().filter(s ->
                    contains(s.getTitle(), keyword) || contains(s.getLastQuestion(), keyword)).collect(Collectors.toList());
        }
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (ChatSessionEntity s : sessions) {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("id", s.getId());
            m.put("title", s.getTitle());
            m.put("scope", s.getScope());
            m.put("lastQuestion", s.getLastQuestion());
            m.put("updatedAt", s.getUpdatedAt() == null ? null : s.getUpdatedAt().format(FMT));
            m.put("group", group(s.getUpdatedAt()));
            m.put("rating", s.getRating());
            list.add(m);
        }
        return list;
    }

    private boolean contains(String source, String k) {
        return source != null && source.toLowerCase().contains(k.toLowerCase());
    }

    private String group(LocalDateTime dt) {
        if (dt == null) return "更早";
        LocalDate d = dt.toLocalDate();
        LocalDate now = LocalDate.now();
        if (d.equals(now)) return "今天";
        if (d.equals(now.minusDays(1))) return "昨天";
        if (d.isAfter(now.minusDays(7))) return "本周";
        return "更早";
    }
}
