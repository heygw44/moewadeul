package com.moewadeul.common.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@IntegrationTest
class SchedulingPoolIntegrationTest {

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @Test
    @DisplayName("[PG-05][F-20] @Scheduled 작업은 스레드 4개를 나눠 써서, 아웃박스 발행기나 메일 발송이 오래 걸려도 결제 만료 같은 작업이 멈추지 않는다")
    void scheduledJobsShareFourThreads() {
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().getCorePoolSize())
                .isEqualTo(4);
    }
}
