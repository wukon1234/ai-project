package com.zhishiyun.kb.admin;

import com.zhishiyun.kb.admin.service.AdminModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时清洗库内残留的 OpenAI 模型配置，避免覆盖 application.yml 中的 DeepSeek 等默认值。
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class AdminModelConfigSanitizer implements ApplicationRunner {

    private final AdminModelService adminModelService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (adminModelService.sanitizeStaleOpenAiConfig()) {
                log.warn("Reset stale admin.model.config (OpenAI leftovers) to yaml defaults");
            }
        } catch (Exception e) {
            log.warn("Model config sanitize skipped: {}", e.getMessage());
        }
    }
}
