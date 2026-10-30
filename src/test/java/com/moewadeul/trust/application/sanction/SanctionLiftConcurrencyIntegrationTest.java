package com.moewadeul.trust.application.sanction;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.trust.application.CompanionReviewFixture;
import com.moewadeul.trust.application.report.MemberReportActionService;
import com.moewadeul.trust.application.report.MemberReportFixture;
import com.moewadeul.trust.domain.sanction.SanctionLiftReason;
import com.moewadeul.trust.domain.sanction.SanctionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SanctionLiftConcurrencyIntegrationTest {

    private static final int ROUNDS = 30;
    private static final long TIMEOUT_SECONDS = 30;

    @Autowired
    private MemberReportActionService memberReportActionService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private SanctionLiftService sanctionLiftService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-21][SN-15] 같은 회원에게 정지 해제와 새 영구 정지 확정이 동시에 와도 회원은 매번 영구 정지 상태로 남는다")
    void liftDoesNotUndoConcurrentlyConfirmedSuspension() throws Exception {
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        SanctionFixture sanctionFixture =
                new SanctionFixture(fixture, new MemberReportFixture(jdbc), memberReportActionService, clock);
        long adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        List<String> mismatches = new ArrayList<>();

        for (int round = 1; round <= ROUNDS; round++) {
            long targetId = fixture.saveVerifiedMember(TestSequence.nickname());
            sanctionFixture.confirm(targetId, adminId, SanctionType.WARNING, "첫 경고");
            long suspensionId = sanctionFixture
                    .confirm(targetId, adminId, SanctionType.SUSPEND_7D, "반복 위반")
                    .sanctionId();
            long permanentReportId = sanctionFixture.receiveReport(targetId);

            runConcurrently(List.of(
                    () -> sanctionLiftService.lift(suspensionId, SanctionLiftReason.END_EARLY.name(), adminId),
                    () -> sanctionFixture.confirmReport(permanentReportId, adminId, SanctionType.PERMANENT, "심각한 위반")));

            Map<String, Object> member =
                    jdbc.queryForMap("SELECT status, suspended_until FROM member WHERE id = ?", targetId);
            if (!"SUSPENDED".equals(member.get("status")) || member.get("suspended_until") != null) {
                mismatches.add("round " + round + ": " + member);
            }
        }

        assertThat(mismatches)
                .as("%d라운드 중 %d라운드에서 회원 상태가 남은 영구 정지와 다름", ROUNDS, mismatches.size())
                .isEmpty();
    }

    private void runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Object> future : futures) {
                future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isTrue();
        }
    }
}
