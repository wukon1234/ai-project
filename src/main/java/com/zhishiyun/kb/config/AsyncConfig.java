package com.zhishiyun.kb.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 异步线程池（如入库 parseAsync）。 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** 入库异步线程池（parseAsync 等）。 */
    @Bean("ingestExecutor")
    public Executor ingestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ingest-");
        executor.initialize();
        return executor;
    }
}
