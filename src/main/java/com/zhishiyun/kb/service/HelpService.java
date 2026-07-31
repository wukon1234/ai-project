package com.zhishiyun.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.entity.HelpFaqEntity;
import com.zhishiyun.kb.mapper.HelpFaqMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 帮助中心：按 locale 读取可配置 FAQ。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HelpService {

    private final HelpFaqMapper helpFaqMapper;

    /** 按 locale 返回 FAQ 列表（缺省 zh-CN）。 */
    public List<Map<String, Object>> listFaqs(String locale) {
        String loc = StringUtils.hasText(locale) ? locale : "zh-CN";
        List<HelpFaqEntity> rows = helpFaqMapper.selectList(new LambdaQueryWrapper<HelpFaqEntity>()
                .eq(HelpFaqEntity::getLocale, loc)
                .orderByAsc(HelpFaqEntity::getSortNo)
                .orderByAsc(HelpFaqEntity::getId));
        log.info("help faq list, locale={}, count={}", loc, rows.size());
        return rows.stream().map(this::toMap).collect(Collectors.toList());
    }

    private Map<String, Object> toMap(HelpFaqEntity e) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("id", e.getId());
        m.put("question", e.getQuestion());
        m.put("answer", e.getAnswer());
        m.put("locale", e.getLocale());
        m.put("sortNo", e.getSortNo());
        return m;
    }
}
