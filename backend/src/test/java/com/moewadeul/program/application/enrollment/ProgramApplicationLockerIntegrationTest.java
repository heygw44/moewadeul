package com.moewadeul.program.application.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.LockWaits;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.idempotency.application.IdempotencyExecutor;
import com.moewadeul.idempotency.application.IdempotentRequest;
import com.moewadeul.idempotency.domain.IdempotencyRecord;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.program.application.ProgramApplyFixture;
import com.moewadeul.program.domain.ProgramApplicationStatus;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

@IntegrationTest
class ProgramApplicationLockerIntegrationTest {

    private static final Instant DUE = MutableClock.DEFAULT_INSTANT.plus(Duration.ofHours(1));

    @Autowired
    private ProgramApplicationLocker locker;

    @Autowired
    private IdempotencyExecutor idempotencyExecutor;

    @Autowired
    private ProgramPaymentService paymentService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private MySQLContainer mysql;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("[F-19] 기본 격리 수준 트랜잭션에서 신청을 잠그려 하면 낡은 스냅숏을 보기 전에 거부한다")
    void lockRejectsRepeatableRead() {
        ProgramApplyFixture fixture = new ProgramApplyFixture(jdbc, memberRepository);
        long programId = fixture.saveProgram(5, false);
        long applicationId = fixture.saveApplication(programId, fixture.saveMember(), "PENDING_PAYMENT", DUE);

        assertThatThrownBy(
                        () -> new TransactionTemplate(transactionManager).execute(status -> locker.lock(applicationId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READ COMMITTED");
    }

    @Test
    @DisplayName("[F-19] 멱등 결제 트랜잭션은 행사 행을 기다리는 사이 커밋된 신청을 잠근 뒤의 일반 읽기로 본다")
    void idempotentPaymentTransactionSeesCommitsMadeWhileWaiting() throws Exception {
        ProgramApplyFixture fixture = new ProgramApplyFixture(jdbc, memberRepository);
        long programId = fixture.saveProgram(5, false);
        long memberId = fixture.saveMember();
        long applicationId = fixture.saveApplication(programId, memberId, "PENDING_PAYMENT", DUE);
        long other = fixture.saveMember();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection holder = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword())) {
            holder.setAutoCommit(false);
            try (Statement s = holder.createStatement()) {
                s.execute("SELECT id FROM program WHERE id = " + programId + " FOR UPDATE");
            }
            IdempotentRequest request =
                    new IdempotentRequest(memberId, TestSequence.unique("locker"), "locker-test", null);
            Future<Integer> count = executor.submit(() ->
                    idempotencyExecutor.executeReadCommitted(request, HttpStatus.OK, Integer.class, () -> {
                        locker.lock(applicationId).orElseThrow();
                        return jdbc.queryForObject(
                                "SELECT COUNT(*) FROM program_application WHERE program_id = ?",
                                Integer.class,
                                programId);
                    }));
            LockWaits.await(mysql, 1, "행사 행");
            try (Statement s = holder.createStatement()) {
                s.execute("INSERT INTO program_application (program_id, member_id, status, payment_due_at, created_at,"
                        + " updated_at) VALUES (" + programId + ", " + other
                        + ", 'CONFIRMED', NOW(6), NOW(6), NOW(6))");
            }
            holder.commit();

            assertThat(count.get(30, TimeUnit.SECONDS)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("[F-19] 결제 선점이 서버 중단으로 버려진 뒤 5분 지나 다시 보낸 결제도 READ COMMITTED로 돌아 신청을 확정한다")
    void paymentThatTakesOverAbandonedClaimRunsReadCommitted() {
        ProgramApplyFixture fixture = new ProgramApplyFixture(jdbc, memberRepository);
        long programId = fixture.saveProgram(5, false);
        long memberId = fixture.saveMember();
        long applicationId = fixture.saveApplication(programId, memberId, "PENDING_PAYMENT", DUE);
        IdempotentRequest request = new IdempotentRequest(memberId, TestSequence.unique("pay"), "pay-test", null);
        idempotencyExecutor.executeReadCommitted(request, HttpStatus.OK, ProgramPayResult.class, () -> null);
        jdbc.update(
                "UPDATE idempotency_record SET response_status = NULL, response_body = NULL"
                        + " WHERE member_id = ? AND idempotency_key = ?",
                memberId,
                request.key());
        clock.advance(IdempotencyRecord.ABANDONED_AFTER);

        ProgramPayResult paid = idempotencyExecutor.executeReadCommitted(
                request, HttpStatus.OK, ProgramPayResult.class, () -> paymentService.pay(memberId, applicationId));

        assertThat(paid.status()).isEqualTo(ProgramApplicationStatus.CONFIRMED);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM program_application WHERE id = ?", String.class, applicationId))
                .isEqualTo("CONFIRMED");
    }
}
