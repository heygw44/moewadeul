package com.moewadeul.trust.application.sanction;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.trust.application.CompanionReviewFixture;
import com.moewadeul.trust.application.report.MemberReportActionResult;
import com.moewadeul.trust.application.report.MemberReportActionService;
import com.moewadeul.trust.application.report.MemberReportFixture;
import com.moewadeul.trust.domain.sanction.SanctionType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SanctionConfirmConcurrencyIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;

    @Autowired
    private MemberReportActionService memberReportActionService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[SN-10] 같은 회원에게 경고를 동시에 2번 확정하면 1건만 저장되고 나머지는 INVALID_INPUT이다")
    void sameLevelIsNotConfirmedTwice() throws Exception {
        // given
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        SanctionFixture sanctionFixture =
                new SanctionFixture(fixture, new MemberReportFixture(jdbc), memberReportActionService, clock);
        long adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        long targetId = fixture.saveVerifiedMember(TestSequence.nickname());
        long firstReportId = sanctionFixture.receiveReport(targetId);
        long secondReportId = sanctionFixture.receiveReport(targetId);

        // when
        List<Throwable> failures =
                runTwice(sanctionFixture, adminId, SanctionType.WARNING, firstReportId, secondReportId);

        // then
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sanction WHERE member_id = ?", Integer.class, targetId))
                .isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[SN-10][SN-12] 같은 회원에게 7일 정지가 계산된 상태에서 동시에 2번 확정해도 정지 제재는 1건이고 데드락이 없다")
    void concurrentSuspensionsAreSerialized() throws Exception {
        // given
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        SanctionFixture sanctionFixture =
                new SanctionFixture(fixture, new MemberReportFixture(jdbc), memberReportActionService, clock);
        long adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        long targetId = fixture.saveVerifiedMember(TestSequence.nickname());
        sanctionFixture.confirm(targetId, adminId, SanctionType.WARNING, "첫 경고");
        long firstReportId = sanctionFixture.receiveReport(targetId);
        long secondReportId = sanctionFixture.receiveReport(targetId);

        // when
        List<Throwable> failures =
                runTwice(sanctionFixture, adminId, SanctionType.SUSPEND_7D, firstReportId, secondReportId);

        // then
        assertThat(failures).hasSize(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sanction WHERE member_id = ? AND type = 'SUSPEND_7D'",
                        Integer.class,
                        targetId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, targetId))
                .isEqualTo("SUSPENDED");
    }

    private List<Throwable> runTwice(
            SanctionFixture sanctionFixture, long adminId, SanctionType type, long firstReportId, long secondReportId)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MemberReportActionResult>> futures = new ArrayList<>();
            for (long reportId : new long[] {firstReportId, secondReportId}) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return sanctionFixture.confirmReport(reportId, adminId, type, "반복 위반");
                }));
            }
            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<MemberReportActionResult> future : futures) {
                try {
                    future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                } catch (TimeoutException e) {
                    failures.add(e);
                }
            }
            return failures;
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isTrue();
        }
    }
}
