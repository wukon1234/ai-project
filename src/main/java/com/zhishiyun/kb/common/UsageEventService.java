package com.zhishiyun.kb.common;

import com.zhishiyun.kb.infra.mysql.entity.UsageEventEntity;
import com.zhishiyun.kb.infra.mysql.mapper.UsageEventMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 统一埋点写入，保证 stats 聚合字段一致。
 * 事件类型：ASK / OPEN_SOURCE / READ_COMPLETE / FAVORITE / FEEDBACK / SEARCH
 */
@Service
@RequiredArgsConstructor
public class UsageEventService {

    private final UsageEventMapper usageEventMapper;

    public void track(Long userId, String eventType, String libraryCode, String refId, String extraJson) {
        if (userId == null || eventType == null || eventType.trim().isEmpty()) {
            return;
        }
        UsageEventEntity entity = new UsageEventEntity();
        entity.setUserId(userId);
        entity.setEventType(eventType.trim());
        entity.setLibraryCode(libraryCode);
        entity.setRefId(refId);
        entity.setExtraJson(extraJson);
        entity.setEventTime(LocalDateTime.now());
        usageEventMapper.insert(entity);
    }
}
