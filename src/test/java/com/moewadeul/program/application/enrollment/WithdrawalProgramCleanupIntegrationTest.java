package com.moewadeul.program.application.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.moewadeul.common.outbox.OutboxLane;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.outbox.application.OutboxPublisher;
import com.moewadeul.program.application.ProgramApplyFixture;
import com.moewadeul.program.domain.ProgramCancelReason;
import com.moewadeul.trust.application.withdrawal.WithdrawalService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
class WithdrawalProgramCleanupIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";
    private static final String REQUEST_IP = "203.0.113.7";

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private ProgramSanctionCleanupService cleanupService;

    @Autowired
    private ProgramVacancyAlertService vacancyAlertService;

    @Autowired
    private ProgramPaymentService paymentService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private ProgramApplyFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
    }

    @Test
    @DisplayName("[F-01][PV-02] 탈퇴하고 발행기가 돌면 결제 대기 신청은 WITHDRAWN으로 취소되고, 자리 반환 이벤트는 남기지만 신청자 취소 알림 이벤트는 남기지 않는다")
    void cancelsPendingWithoutApplicantNotification() {
        long member = saveMemberWithPassword();
        long waiting = fixture.saveMember();
        long programA = fixture.saveProgram(1, false);
        long programB = fixture.saveProgram(3, false);
        long pending = fixture.saveApplication(programA, member, "PENDING_PAYMENT");
        long confirmed = fixture.saveApplication(programB, member, "CONFIRMED");
        long otherPending = fixture.saveApplication(programB, waiting, "PENDING_PAYMENT");
        fixture.saveVacancyAlert(programA, waiting);

        withdrawalService.withdraw(member, PASSWORD, REQUEST_IP);
        assertThat(fixture.applicationStatus(pending)).isEqualTo("PENDING_PAYMENT");
        outboxPublisher.publishPending(OutboxLane.DEFAULT);
        outboxPublisher.publishPending(OutboxLane.DEFAULT);

        assertThat(fixture.applicationStatus(pending)).isEqualTo("CANCELED");
        assertThat(cancelReason(pending)).isEqualTo("WITHDRAWN");
        assertThat(fixture.outboxCount("PROGRAM_APPLICATION_CANCELED", pending)).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM notification WHERE member_id = ? AND type = 'PROGRAM_APPLICATION_CANCELED'",
                        Integer.class,
                        member))
                .isZero();
        List<String> payloads = fixture.seatReleasedPayloads(programA);
        assertThat(payloads).hasSize(1);
        List<Number> memberIds = JsonPath.read(payloads.get(0), "$.memberIds");
        assertThat(memberIds.stream().map(Number::longValue).toList()).containsExactly(waiting);
        assertThat(fixture.applicationStatus(confirmed)).isEqualTo("CONFIRMED");
        assertThat(reviewRequestedAt(confirmed)).isNotNull();
        assertThat(fixture.applicationStatus(otherPending)).isEqualTo("PENDING_PAYMENT");
        assertThat(reviewRequestedAt(otherPending)).isNull();
    }

    @Test
    @DisplayName("[F-01][PV-14][PG-07] 탈퇴하면 빈자리 알림 신청이 모두 지워져서, 정리로 자리가 돌아오거나 다른 신청이 취소돼도 탈퇴한 회원은 알림 대상이 아니다")
    void withdrawalRemovesVacancyAlerts() {
        long member = saveMemberWithPassword();
        long waiting = fixture.saveMember();
        long holder = fixture.saveMember();
        long canceler = fixture.saveMember();
        long programA = fixture.saveProgram(1, false);
        long programB = fixture.saveProgram(2, false);
        fixture.saveApplication(programA, member, "PENDING_PAYMENT");
        fixture.saveVacancyAlert(programA, member);
        fixture.saveVacancyAlert(programA, waiting);
        fixture.saveApplication(programB, holder, "CONFIRMED");
        long cancelerApplicationId = fixture.saveApplication(programB, canceler, "PENDING_PAYMENT");
        vacancyAlertService.subscribe(member, programB);
        fixture.saveVacancyAlert(programB, waiting);

        withdrawalService.withdraw(member, PASSWORD, REQUEST_IP);
        outboxPublisher.publishPending(OutboxLane.DEFAULT);
        outboxPublisher.publishPending(OutboxLane.DEFAULT);
        paymentService.cancel(canceler, cancelerApplicationId);
        outboxPublisher.publishPending(OutboxLane.DEFAULT);

        assertThat(fixture.vacancyAlertCount(programA, member)).isZero();
        assertThat(fixture.vacancyAlertCount(programB, member)).isZero();
        assertThat(seatReleasedMemberIds(programA)).containsExactly(waiting);
        assertThat(seatReleasedMemberIds(programB)).containsExactly(waiting);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM notification WHERE member_id = ? AND type = 'PROGRAM_SEAT_RELEASED'",
                        Integer.class,
                        member))
                .isZero();
    }

    @Test
    @DisplayName("[F-01][NFR-04] 같은 정리를 다시 실행해도 이벤트가 늘지 않고 처음 기록한 취소·확인 요청 시각이 유지된다")
    void rerunChangesNothing() {
        long member = saveMemberWithPassword();
        long waiting = fixture.saveMember();
        long programA = fixture.saveProgram(1, false);
        long programB = fixture.saveProgram(3, false);
        long pending = fixture.saveApplication(programA, member, "PENDING_PAYMENT");
        long confirmed = fixture.saveApplication(programB, member, "CONFIRMED");
        fixture.saveVacancyAlert(programA, waiting);
        withdrawalService.withdraw(member, PASSWORD, REQUEST_IP);
        outboxPublisher.publishPending(OutboxLane.DEFAULT);
        LocalDateTime firstReviewAt = reviewRequestedAt(confirmed);
        LocalDateTime firstCanceledAt = canceledAt(pending);

        clock.advance(Duration.ofHours(1));
        cleanupService.cleanUp(member, ProgramCancelReason.WITHDRAWN);

        assertThat(fixture.seatReleasedPayloads(programA)).hasSize(1);
        assertThat(reviewRequestedAt(confirmed)).isEqualTo(firstReviewAt);
        assertThat(canceledAt(pending)).isEqualTo(firstCanceledAt);
        assertThat(cancelReason(pending)).isEqualTo("WITHDRAWN");
    }

    private List<Long> seatReleasedMemberIds(long programId) {
        return fixture.seatReleasedPayloads(programId).stream()
                .flatMap(payload -> JsonPath.<List<Number>>read(payload, "$.memberIds").stream())
                .map(Number::longValue)
                .toList();
    }

    private long saveMemberWithPassword() {
        long memberId = fixture.saveMember();
        jdbc.update(
                "UPDATE member SET password_hash = ?, status = 'ACTIVE', email_verified_at = NOW(6) WHERE id = ?",
                passwordEncoder.encode(PASSWORD),
                memberId);
        return memberId;
    }

    private String cancelReason(long applicationId) {
        return jdbc.queryForObject(
                "SELECT cancel_reason FROM program_application WHERE id = ?", String.class, applicationId);
    }

    private LocalDateTime canceledAt(long applicationId) {
        return jdbc.queryForObject(
                "SELECT canceled_at FROM program_application WHERE id = ?", LocalDateTime.class, applicationId);
    }

    private LocalDateTime reviewRequestedAt(long applicationId) {
        return jdbc.queryForObject(
                "SELECT review_requested_at FROM program_application WHERE id = ?", LocalDateTime.class, applicationId);
    }
}
