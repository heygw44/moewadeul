package com.moewadeul.common.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.moewadeul.aisearch.application.AiSearchUsageCleanupScheduler;
import com.moewadeul.aisearch.application.AiSearchUsageCleanupService;
import com.moewadeul.member.application.login.LoginHistoryCleanupScheduler;
import com.moewadeul.member.application.login.LoginHistoryCleanupService;
import com.moewadeul.member.application.verification.EmailVerificationCleanupScheduler;
import com.moewadeul.member.application.verification.EmailVerificationCleanupService;
import com.moewadeul.outbox.application.OutboxEventCleaner;
import com.moewadeul.outbox.application.OutboxEventCleanupScheduler;
import com.moewadeul.trust.application.report.ReportContentCleanupScheduler;
import com.moewadeul.trust.application.report.ReportContentCleanupService;
import com.moewadeul.trust.application.sanction.SanctionExpiryScheduler;
import com.moewadeul.trust.application.sanction.SanctionExpiryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScheduledCleanupJobFailureTest {

    private static final IllegalStateException FAILURE = new IllegalStateException("DB 연결 실패");

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ScheduledJobRunner runner = new ScheduledJobRunner(new ScheduledJobMetrics(meterRegistry));

    @Test
    @DisplayName("[NFR-09][PV-11] AI 사용 기록 정리는 실패를 삼키고 작업 실패 수를 올린다")
    void aiSearchUsageCleanup() {
        AiSearchUsageCleanupService service = mock(AiSearchUsageCleanupService.class);
        AiSearchUsageCleanupScheduler scheduler = new AiSearchUsageCleanupScheduler(service, runner);

        scheduler.cleanUp();
        verify(service).deleteExpiredUsage();
        assertThat(failures("ai-search-usage-cleanup")).isZero();

        doThrow(FAILURE).when(service).deleteExpiredUsage();
        scheduler.cleanUp();
        assertThat(failures("ai-search-usage-cleanup")).isEqualTo(1);
    }

    @Test
    @DisplayName("[NFR-09][PV-12] 인증 기록 정리는 실패를 삼키고 작업 실패 수를 올린다")
    void emailVerificationCleanup() {
        EmailVerificationCleanupService service = mock(EmailVerificationCleanupService.class);
        EmailVerificationCleanupScheduler scheduler = new EmailVerificationCleanupScheduler(service, runner);

        scheduler.cleanUp();
        verify(service).deleteExpired();
        assertThat(failures("email-verification-cleanup")).isZero();

        doThrow(FAILURE).when(service).deleteExpired();
        scheduler.cleanUp();
        assertThat(failures("email-verification-cleanup")).isEqualTo(1);
    }

    @Test
    @DisplayName("[NFR-09][PV-06] 로그인 기록 정리는 실패를 삼키고 작업 실패 수를 올린다")
    void loginHistoryCleanup() {
        LoginHistoryCleanupService service = mock(LoginHistoryCleanupService.class);
        LoginHistoryCleanupScheduler scheduler = new LoginHistoryCleanupScheduler(service, runner);

        scheduler.cleanUp();
        verify(service).deleteExpired();
        assertThat(failures("login-history-cleanup")).isZero();

        doThrow(FAILURE).when(service).deleteExpired();
        scheduler.cleanUp();
        assertThat(failures("login-history-cleanup")).isEqualTo(1);
    }

    @Test
    @DisplayName("[NFR-09][NFR-04] 발행한 이벤트 정리는 실패를 삼키고 작업 실패 수를 올린다")
    void outboxEventCleanup() {
        OutboxEventCleaner cleaner = mock(OutboxEventCleaner.class);
        OutboxEventCleanupScheduler scheduler = new OutboxEventCleanupScheduler(cleaner, runner);

        scheduler.cleanUp();
        verify(cleaner).deleteExpired();
        assertThat(failures("outbox-event-cleanup")).isZero();

        doThrow(FAILURE).when(cleaner).deleteExpired();
        scheduler.cleanUp();
        assertThat(failures("outbox-event-cleanup")).isEqualTo(1);
    }

    @Test
    @DisplayName("[NFR-09][PV-07] 회원 신고·제재 내용 정리는 실패를 삼키고 작업 실패 수를 올린다")
    void reportContentCleanup() {
        ReportContentCleanupService service = mock(ReportContentCleanupService.class);
        ReportContentCleanupScheduler scheduler = new ReportContentCleanupScheduler(service, runner);

        scheduler.cleanUp();
        verify(service).clearExpiredContent();
        assertThat(failures("report-content-cleanup")).isZero();

        doThrow(FAILURE).when(service).clearExpiredContent();
        scheduler.cleanUp();
        assertThat(failures("report-content-cleanup")).isEqualTo(1);
    }

    @Test
    @DisplayName("[NFR-09][SN-10] 제재 만료 처리는 실패를 삼키고 작업 실패 수를 올린다")
    void sanctionExpiry() {
        SanctionExpiryService service = mock(SanctionExpiryService.class);
        SanctionExpiryScheduler scheduler = new SanctionExpiryScheduler(service, runner);

        scheduler.expire();
        verify(service).run();
        assertThat(failures("sanction-expiry")).isZero();

        doThrow(FAILURE).when(service).run();
        scheduler.expire();
        assertThat(failures("sanction-expiry")).isEqualTo(1);
    }

    private double failures(String job) {
        return meterRegistry
                .get("moewadeul.scheduled.job.failures")
                .tag("job", job)
                .counter()
                .count();
    }
}
