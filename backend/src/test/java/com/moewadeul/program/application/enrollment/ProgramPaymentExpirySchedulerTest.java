package com.moewadeul.program.application.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.scheduling.ScheduledJobRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProgramPaymentExpirySchedulerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ScheduledJobMetrics metrics = new ScheduledJobMetrics(meterRegistry);

    @Test
    @DisplayName("[NFR-09][PG-05] 결제 만료 처리가 예외로 끝나면 스케줄러는 예외를 삼키고 작업 실패 수를 1 올린다")
    void countsExpiryFailure() {
        ProgramPaymentExpiryService service = mock(ProgramPaymentExpiryService.class);
        doThrow(new IllegalStateException("DB 연결 실패")).when(service).run();

        new ProgramPaymentExpiryScheduler(service, new ScheduledJobRunner(metrics)).expire();

        assertThat(meterRegistry
                        .get("moewadeul.scheduled.job.failures")
                        .tag("job", "program-payment-expiry")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[NFR-09][PG-05] 신청 하나의 만료 처리가 실패해 결과에 실패 수가 있으면 스케줄러는 작업 실패 수를 1 올린다")
    void countsFailureWhenSomeApplicationFailed() {
        ProgramPaymentExpiryService service = mock(ProgramPaymentExpiryService.class);
        when(service.run()).thenReturn(new ProgramPaymentExpiryService.Result(4, 2));

        new ProgramPaymentExpiryScheduler(service, new ScheduledJobRunner(metrics)).expire();

        assertThat(failureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[NFR-09][PG-05] 실패한 신청이 없으면 작업 실패 수를 올리지 않는다")
    void doesNotCountFailureWhenNothingFailed() {
        ProgramPaymentExpiryService service = mock(ProgramPaymentExpiryService.class);
        when(service.run()).thenReturn(new ProgramPaymentExpiryService.Result(4, 0));

        new ProgramPaymentExpiryScheduler(service, new ScheduledJobRunner(metrics)).expire();

        assertThat(failureCount()).isZero();
    }

    private double failureCount() {
        return meterRegistry
                .get("moewadeul.scheduled.job.failures")
                .tag("job", "program-payment-expiry")
                .counter()
                .count();
    }
}
