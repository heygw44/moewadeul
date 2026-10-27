package com.moewadeul.publicdata.application;

import com.moewadeul.publicdata.domain.SyncJobType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class SyncJobExecutorConfig {

    public static final String SYNC_JOB_EXECUTOR = "syncJobExecutor";

    private static final int POOL_SIZE = SyncJobType.values().length;

    @Bean(SYNC_JOB_EXECUTOR)
    ThreadPoolTaskExecutor syncJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("sync-job-");
        executor.setCorePoolSize(POOL_SIZE);
        executor.setMaxPoolSize(POOL_SIZE);
        executor.setQueueCapacity(0);
        return executor;
    }
}
