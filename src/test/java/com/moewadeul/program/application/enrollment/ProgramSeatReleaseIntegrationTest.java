package com.moewadeul.program.application.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.program.application.ProgramApplyFixture;
import com.moewadeul.program.application.catalog.ProgramAdminService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ProgramSeatReleaseIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;

    @Autowired
    private ProgramPaymentService programPaymentService;

    @Autowired
    private ProgramPaymentExpiryService expiryService;

    @Autowired
    private ProgramAdminService programAdminService;

    @Autowired
    private MemberJpaRepository memberRepository;

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
    @DisplayName("[F-20][PG-07] 본인 취소로 자리가 돌아오면 이벤트가 1건 생기고, 이미 신청이 있는 알림 신청자는 빠지며 대상은 알림 신청 순서이고 알림 시각이 적힌다")
    void userCancelRecordsSeatReleased() {
        // given
        long programId = fixture.saveProgram(2, false);
        long canceler = fixture.saveMember();
        long holder = fixture.saveMember();
        long waiting = fixture.saveMember();
        long cancelApplicationId = fixture.saveApplication(programId, canceler, "PENDING_PAYMENT");
        fixture.saveApplication(programId, holder, "CONFIRMED");
        fixture.saveVacancyAlert(programId, waiting);
        fixture.saveVacancyAlert(programId, holder);
        fixture.saveVacancyAlert(programId, canceler);

        // when
        programPaymentService.cancel(canceler, cancelApplicationId);

        // then
        assertSeatReleased(programId, List.of(waiting, canceler));
        assertThat(fixture.vacancyAlertNotifiedAt(programId, waiting)).isEqualTo(LocalDateTime.of(2026, 10, 5, 3, 0));
        assertThat(fixture.vacancyAlertNotifiedAt(programId, holder)).isNull();
    }

    @Test
    @DisplayName("[F-20][PG-07] 결제 기한 만료로 자리가 돌아와도 같은 이벤트가 1건 생긴다")
    void expiryRecordsSeatReleased() {
        // given
        long programId = fixture.saveProgram(2, false);
        long expiring = fixture.saveMember();
        long holder = fixture.saveMember();
        long waiting = fixture.saveMember();
        fixture.saveApplication(programId, expiring, "PENDING_PAYMENT", NOW.minus(Duration.ofMinutes(1)));
        fixture.saveApplication(programId, holder, "PENDING_PAYMENT");
        fixture.saveVacancyAlert(programId, waiting);
        fixture.saveVacancyAlert(programId, holder);
        fixture.saveVacancyAlert(programId, expiring);

        // when
        expiryService.run();

        // then
        assertSeatReleased(programId, List.of(waiting, expiring));
        assertThat(fixture.vacancyAlertNotifiedAt(programId, waiting)).isNotNull();
        assertThat(fixture.vacancyAlertNotifiedAt(programId, holder)).isNull();
    }

    @Test
    @DisplayName("[F-20][PG-07] 결제 기한이 지난 자리를 다른 회원이 먼저 신청해 가져갔으면, 뒤늦게 만료 처리해도 남은 자리가 없으므로 이벤트가 없다")
    void expiryRecordsNothingWhenSeatAlreadyTaken() {
        // given
        long programId = fixture.saveProgram(1, false);
        long expiring = fixture.saveMember();
        long newcomer = fixture.saveMember();
        long waiting = fixture.saveMember();
        long expiringApplicationId =
                fixture.saveApplication(programId, expiring, "PENDING_PAYMENT", NOW.minus(Duration.ofMinutes(1)));
        fixture.saveApplication(programId, newcomer, "PENDING_PAYMENT");
        fixture.saveVacancyAlert(programId, waiting);

        // when
        expiryService.run();

        // then
        assertThat(fixture.applicationStatus(expiringApplicationId)).isEqualTo("EXPIRED");
        assertThat(fixture.seatReleasedPayloads(programId)).isEmpty();
        assertThat(fixture.vacancyAlertNotifiedAt(programId, waiting)).isNull();
    }

    @Test
    @DisplayName("[F-20][PG-07] 결제 기한이 지난 신청을 본인이 취소했는데 그 자리를 다른 회원이 이미 가져갔으면 이벤트가 없다")
    void userCancelRecordsNothingWhenSeatAlreadyTaken() {
        // given
        long programId = fixture.saveProgram(1, false);
        long canceler = fixture.saveMember();
        long newcomer = fixture.saveMember();
        long waiting = fixture.saveMember();
        long cancelApplicationId =
                fixture.saveApplication(programId, canceler, "PENDING_PAYMENT", NOW.minus(Duration.ofMinutes(1)));
        fixture.saveApplication(programId, newcomer, "PENDING_PAYMENT");
        fixture.saveVacancyAlert(programId, waiting);

        // when
        programPaymentService.cancel(canceler, cancelApplicationId);

        // then
        assertThat(fixture.applicationStatus(cancelApplicationId)).isEqualTo("CANCELED");
        assertThat(fixture.seatReleasedPayloads(programId)).isEmpty();
        assertThat(fixture.vacancyAlertNotifiedAt(programId, waiting)).isNull();
    }

    @Test
    @DisplayName("[F-20][PG-07] 마지막 알림 뒤 30분이 되기 전에 자리가 또 돌아오면 이미 알린 회원은 빠지고 그 뒤에 알림을 신청한 회원만 대상이다")
    void releaseWithinRenotifyIntervalSkipsRecentlyNotified() {
        // given
        long programId = fixture.saveProgram(3, false);
        long firstCanceler = fixture.saveMember();
        long secondCanceler = fixture.saveMember();
        long notified = fixture.saveMember();
        long lateSubscriber = fixture.saveMember();
        long firstApplicationId = fixture.saveApplication(programId, firstCanceler, "PENDING_PAYMENT");
        long secondApplicationId = fixture.saveApplication(programId, secondCanceler, "PENDING_PAYMENT");
        fixture.saveVacancyAlert(programId, notified);
        programPaymentService.cancel(firstCanceler, firstApplicationId);
        fixture.saveVacancyAlert(programId, lateSubscriber);
        clock.advance(Duration.ofMinutes(30).minusSeconds(1));

        // when
        programPaymentService.cancel(secondCanceler, secondApplicationId);

        // then
        List<String> payloads = fixture.seatReleasedPayloads(programId);
        assertThat(payloads).hasSize(2);
        assertThat(memberIds(payloads.get(1))).containsExactly(lateSubscriber);
        assertThat(fixture.vacancyAlertNotifiedAt(programId, notified)).isEqualTo(LocalDateTime.of(2026, 10, 5, 3, 0));
    }

    @Test
    @DisplayName("[F-20][PG-07] 마지막 알림 뒤 30분이 지나 자리가 또 돌아오면 같은 회원에게 다시 알린다")
    void releaseAfterRenotifyIntervalNotifiesAgain() {
        // given
        long programId = fixture.saveProgram(3, false);
        long firstCanceler = fixture.saveMember();
        long secondCanceler = fixture.saveMember();
        long waiting = fixture.saveMember();
        long firstApplicationId = fixture.saveApplication(programId, firstCanceler, "PENDING_PAYMENT");
        long secondApplicationId = fixture.saveApplication(programId, secondCanceler, "PENDING_PAYMENT");
        fixture.saveVacancyAlert(programId, waiting);
        programPaymentService.cancel(firstCanceler, firstApplicationId);
        clock.advance(Duration.ofMinutes(30));

        // when
        programPaymentService.cancel(secondCanceler, secondApplicationId);

        // then
        List<String> payloads = fixture.seatReleasedPayloads(programId);
        assertThat(payloads).hasSize(2);
        assertThat(memberIds(payloads.get(1))).containsExactly(waiting);
        assertThat(fixture.vacancyAlertNotifiedAt(programId, waiting)).isEqualTo(LocalDateTime.of(2026, 10, 5, 3, 30));
    }

    @Test
    @DisplayName("[F-20][PG-07] 관리자가 행사를 취소하면 자리가 생긴 것이 아니므로 이벤트가 없다")
    void adminProgramCancelRecordsNothing() {
        long programId = fixture.saveProgram(2, false);
        long applicant = fixture.saveMember();
        long waiting = fixture.saveMember();
        fixture.saveApplication(programId, applicant, "PENDING_PAYMENT");
        fixture.saveVacancyAlert(programId, waiting);

        programAdminService.cancel(fixture.saveMember(), programId);

        assertThat(fixture.seatReleasedPayloads(programId)).isEmpty();
        assertThat(fixture.vacancyAlertNotifiedAt(programId, waiting)).isNull();
    }

    @Test
    @DisplayName("[F-20][PG-07] 알림 신청자가 없으면 이벤트가 없다")
    void noSubscribersRecordsNothing() {
        long programId = fixture.saveProgram(2, false);
        long canceler = fixture.saveMember();
        long applicationId = fixture.saveApplication(programId, canceler, "PENDING_PAYMENT");

        programPaymentService.cancel(canceler, applicationId);

        assertThat(fixture.seatReleasedPayloads(programId)).isEmpty();
    }

    @Test
    @DisplayName("[F-20][PG-07] 알림 신청자가 모두 결제 대기·확정 신청을 가졌으면 이벤트가 없다")
    void allSubscribersHoldSeatsRecordsNothing() {
        long programId = fixture.saveProgram(3, false);
        long canceler = fixture.saveMember();
        long holder = fixture.saveMember();
        long pending = fixture.saveMember();
        long applicationId = fixture.saveApplication(programId, canceler, "PENDING_PAYMENT");
        fixture.saveApplication(programId, holder, "CONFIRMED");
        fixture.saveApplication(programId, pending, "PENDING_PAYMENT");
        fixture.saveVacancyAlert(programId, holder);
        fixture.saveVacancyAlert(programId, pending);

        programPaymentService.cancel(canceler, applicationId);

        assertThat(fixture.seatReleasedPayloads(programId)).isEmpty();
        assertThat(fixture.vacancyAlertNotifiedAt(programId, holder)).isNull();
    }

    @Test
    @DisplayName("[F-20][PG-07] 신청 마감 시각이 지난 뒤의 본인 취소와 결제 만료는 이벤트를 만들지 않는다")
    void afterApplyCloseRecordsNothing() {
        // given
        long programId = fixture.saveProgram(
                2, false, NOW.minus(Duration.ofDays(1)), NOW.plus(Duration.ofHours(1)), "SCHEDULED");
        long canceler = fixture.saveMember();
        long expiring = fixture.saveMember();
        long waiting = fixture.saveMember();
        long cancelApplicationId = fixture.saveApplication(programId, canceler, "PENDING_PAYMENT");
        fixture.saveApplication(programId, expiring, "PENDING_PAYMENT", NOW.plus(Duration.ofMinutes(90)));
        fixture.saveVacancyAlert(programId, waiting);
        clock.advance(Duration.ofMinutes(61));

        // when
        programPaymentService.cancel(canceler, cancelApplicationId);
        clock.advance(Duration.ofMinutes(30));
        expiryService.run();

        // then
        assertThat(fixture.applicationStatus(cancelApplicationId)).isEqualTo("CANCELED");
        assertThat(fixture.seatReleasedPayloads(programId)).isEmpty();
        assertThat(fixture.vacancyAlertNotifiedAt(programId, waiting)).isNull();
    }

    private void assertSeatReleased(long programId, List<Long> expectedMemberIds) {
        List<String> payloads = fixture.seatReleasedPayloads(programId);
        assertThat(payloads).hasSize(1);
        assertThat(JsonPath.<Number>read(payloads.get(0), "$.programId").longValue())
                .isEqualTo(programId);
        assertThat(memberIds(payloads.get(0))).containsExactlyElementsOf(expectedMemberIds);
    }

    private static List<Long> memberIds(String payload) {
        List<Number> memberIds = JsonPath.read(payload, "$.memberIds");
        return memberIds.stream().map(Number::longValue).toList();
    }
}
