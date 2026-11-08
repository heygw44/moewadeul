package com.moewadeul.trust.application.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.LockWaits;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.trust.application.identity.IdentityVerificationService;
import com.moewadeul.trust.application.identity.IdentityVerifyCommand;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

@IntegrationTest
class WithdrawalIdentityVerifyRaceIntegrationTest {

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private WithdrawalApplier withdrawalApplier;

    @Autowired
    private IdentityVerificationService identityVerificationService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MySQLContainer mysql;

    @Test
    @DisplayName("탈퇴 트랜잭션이 커밋되기 전에 들어온 본인확인은 탈퇴가 끝난 뒤 거절되고 본인확인 행을 남기지 않는다")
    void verifyArrivingDuringWithdrawalIsRejected() throws Exception {
        // given
        long memberId = memberFixture.save().getId();
        CountDownLatch applied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> withdrawal = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                withdrawalApplier.apply(memberId);
                applied.countDown();
                awaitQuietly(release);
            }));
            assertThat(applied.await(10, TimeUnit.SECONDS)).isTrue();

            // when
            Future<?> verify = executor.submit(() -> identityVerificationService.verify(
                    memberId, new IdentityVerifyCommand(1995, Gender.FEMALE, "race-person")));
            LockWaits.waitUntilBlockedOrDone(mysql, 1, verify);
            release.countDown();
            withdrawal.get(10, TimeUnit.SECONDS);

            // then
            assertThatThrownBy(() -> verify.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.AUTHENTICATION_REQUIRED));
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM identity_verification WHERE member_id = ?", Integer.class, memberId))
                    .isZero();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
