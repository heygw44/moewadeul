package com.moewadeul.program.application.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.program.application.ProgramApplyFixture;
import com.moewadeul.program.domain.ProgramCancelReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ProgramReadCommittedGuardIntegrationTest {

    @Autowired
    private ProgramPaymentService paymentService;

    @Autowired
    private ProgramVacancyAlertService vacancyAlertService;

    @Autowired
    private ProgramSanctionCleanupApplier cleanupApplier;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private ProgramApplyFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
    }

    @Test
    @DisplayName("[F-19][PG-07] 기본 격리 수준의 바깥 트랜잭션에 합류해서 부른 본인 취소는 READ COMMITTED가 아니므로 실패한다")
    void cancelJoiningDefaultTransactionFails() {
        long programId = fixture.saveProgram(2, false);
        long member = fixture.saveMember();
        long applicationId = fixture.saveApplication(programId, member, "PENDING_PAYMENT");

        assertThatThrownBy(() -> inTransaction(
                        TransactionDefinition.ISOLATION_DEFAULT, () -> paymentService.cancel(member, applicationId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READ COMMITTED");
        assertThat(fixture.applicationStatus(applicationId)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("[F-20][PG-07] 기본 격리 수준의 바깥 트랜잭션에 합류해서 부른 빈자리 알림 신청은 READ COMMITTED가 아니므로 실패한다")
    void subscribeJoiningDefaultTransactionFails() {
        long programId = fixture.saveProgram(2, false);
        fixture.fillSeats(programId, 2);
        long member = fixture.saveMember();

        assertThatThrownBy(() -> inTransaction(
                        TransactionDefinition.ISOLATION_DEFAULT,
                        () -> vacancyAlertService.subscribe(member, programId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READ COMMITTED");
        assertThat(fixture.vacancyAlertCount(programId, member)).isZero();
    }

    @Test
    @DisplayName("[F-16][PG-07] 기본 격리 수준의 바깥 트랜잭션에 합류해서 부른 제재 정리의 신청 취소는 READ COMMITTED가 아니므로 실패한다")
    void cancelPendingJoiningDefaultTransactionFails() {
        long programId = fixture.saveProgram(2, false);
        long member = fixture.saveMember();
        long applicationId = fixture.saveApplication(programId, member, "PENDING_PAYMENT");

        assertThatThrownBy(() -> inTransaction(
                        TransactionDefinition.ISOLATION_DEFAULT,
                        () -> cleanupApplier.cancelPending(applicationId, member, ProgramCancelReason.SANCTIONED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READ COMMITTED");
        assertThat(fixture.applicationStatus(applicationId)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("[F-20][PG-07] 바깥 트랜잭션이 READ COMMITTED이면 합류해서 불러도 빈자리 알림 신청이 된다")
    void subscribeJoiningReadCommittedTransactionSucceeds() {
        long programId = fixture.saveProgram(2, false);
        fixture.fillSeats(programId, 2);
        long member = fixture.saveMember();

        inTransaction(
                TransactionDefinition.ISOLATION_READ_COMMITTED, () -> vacancyAlertService.subscribe(member, programId));

        assertThat(fixture.vacancyAlertCount(programId, member)).isEqualTo(1);
    }

    private void inTransaction(int isolationLevel, Runnable action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(isolationLevel);
        template.executeWithoutResult(status -> action.run());
    }
}
