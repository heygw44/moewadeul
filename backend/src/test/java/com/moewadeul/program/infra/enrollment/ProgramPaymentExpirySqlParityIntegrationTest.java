package com.moewadeul.program.infra.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.program.application.ProgramApplyFixture;
import com.moewadeul.program.domain.ProgramApplication;
import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.program.domain.ProgramCancelReason;
import com.moewadeul.program.domain.ProgramErrorCode;
import com.moewadeul.program.infra.ProgramApplicationJpaRepository;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ProgramPaymentExpirySqlParityIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT.plusNanos(123_456_000);

    private static final String COLUMNS = "status, cancel_reason, canceled_at, confirmed_at, updated_at";

    @Autowired
    private ProgramPaymentExpiryMapper mapper;

    @Autowired
    private ProgramApplicationJpaRepository applicationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private ProgramApplyFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
    }

    static Stream<Arguments> everyStatusAroundPaymentDue() {
        return Stream.of(ProgramApplicationStatus.values())
                .flatMap(
                        status -> Stream.of(-1L, 0L, 1L).map(secondsAfterDue -> Arguments.of(status, secondsAfterDue)));
    }

    @ParameterizedTest(name = "{0} / 기한 {1}초 뒤에 확인")
    @MethodSource("everyStatusAroundPaymentDue")
    @DisplayName("SQL 만료는 도메인이 결제 기한이 지났다고 보는 결제 대기 신청만 바꾸고, 바뀐 행은 도메인이 만료로 읽는다")
    void expiryAgreesWithDomainDueRule(ProgramApplicationStatus status, long secondsAfterDue) {
        // given
        Instant paymentDueAt = NOW.minusSeconds(secondsAfterDue);
        long programId = fixture.saveProgram(2, false);
        long sqlSide = fixture.saveApplication(programId, fixture.saveMember(), status.name(), paymentDueAt);
        long domainSide = fixture.saveApplication(programId, fixture.saveMember(), status.name(), paymentDueAt);
        Map<String, Object> before = rowOf(sqlSide);

        // when
        boolean domainRejectsAsExpired = rejectsPayment(domainSide) == ProgramErrorCode.PROGRAM_PAYMENT_EXPIRED;
        boolean selectedAsDue =
                mapper.selectDueTargets(NOW, 0, 100).stream().anyMatch(target -> target.applicationId() == sqlSide)
                        && mapper.selectOverdueByMember(programId, memberOf(sqlSide), NOW) != null;
        int changed = mapper.expireIfDue(sqlSide, NOW);

        // then
        boolean pendingPayment = status == ProgramApplicationStatus.PENDING_PAYMENT;
        assertThat(changed == 1).isEqualTo(pendingPayment && domainRejectsAsExpired);
        assertThat(selectedAsDue).isEqualTo(changed == 1);
        if (changed == 0) {
            assertThat(rowOf(sqlSide)).isEqualTo(before);
            return;
        }
        assertThat(rowOf(sqlSide))
                .containsEntry("status", ProgramApplicationStatus.EXPIRED.name())
                .containsEntry("cancel_reason", ProgramCancelReason.EXPIRED.name());
        ProgramApplication expired = applicationRepository.findById(sqlSide).orElseThrow();
        assertThat(expired.getCanceledAt()).isEqualTo(NOW);
        assertThat(expired.getUpdatedAt()).isEqualTo(NOW);
        assertThat(expired.getConfirmedAt()).isNull();
        assertThat(rejectsPayment(sqlSide)).isEqualTo(ProgramErrorCode.PROGRAM_PAYMENT_EXPIRED);
    }

    private ProgramErrorCode rejectsPayment(long applicationId) {
        return transactionTemplate.execute(tx -> {
            tx.setRollbackOnly();
            ProgramApplication application =
                    applicationRepository.findById(applicationId).orElseThrow();
            try {
                application.confirmPayment(NOW);
            } catch (BusinessException e) {
                return (ProgramErrorCode) e.getErrorCode();
            }
            return null;
        });
    }

    private long memberOf(long applicationId) {
        return jdbc.queryForObject("SELECT member_id FROM program_application WHERE id = ?", Long.class, applicationId);
    }

    private Map<String, Object> rowOf(long applicationId) {
        return jdbc.queryForMap("SELECT " + COLUMNS + " FROM program_application WHERE id = ?", applicationId);
    }
}
